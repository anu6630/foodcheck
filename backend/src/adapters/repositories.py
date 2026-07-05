import re
from typing import List, Optional, Tuple
from sqlalchemy.ext.asyncio import AsyncSession

from sqlalchemy import select, or_, func
from sqlalchemy.orm import selectinload

from src.domain.entities import Product, Ingredient, ProductIngredient, IngredientBan, IngredientName
from src.domain.interfaces import IProductRepository, IIngredientRepository
from src.adapters.models import DBProduct, DBProductIngredient, DBIngredient, DBIngredientName, DBIngredientBan

def to_domain_ban(db_ban: DBIngredientBan) -> IngredientBan:
    return IngredientBan(
        country_code=db_ban.country_code,
        status=db_ban.status,
        condition=db_ban.condition,
        regulation_ref=db_ban.regulation_ref,
        effective_date=db_ban.effective_date
    )

def to_domain_name(db_name: DBIngredientName) -> IngredientName:
    return IngredientName(
        lang=db_name.lang,
        name=db_name.name
    )

def to_domain_ingredient(db_ing: DBIngredient) -> Ingredient:
    return Ingredient(
        id=db_ing.id,
        name=db_ing.name,
        category=db_ing.category,
        e_number=db_ing.e_number,
        inci_name=db_ing.inci_name,
        cas_number=db_ing.cas_number,
        names=[to_domain_name(n) for n in db_ing.names],
        bans=[to_domain_ban(b) for b in db_ing.bans]
    )

def to_domain_product(db_prod: DBProduct) -> Product:
    ingredients = []
    for pi in db_prod.ingredients:
        ingredients.append(ProductIngredient(
            ingredient=to_domain_ingredient(pi.ingredient),
            order=pi.order
        ))
    
    return Product(
        id=db_prod.id,
        name=db_prod.name,
        brand=db_prod.brand,
        barcode=db_prod.barcode,
        category=db_prod.category,
        source=db_prod.source,
        ingredients=ingredients
    )

class SQLAlchemyProductRepository(IProductRepository):
    def __init__(self, session: AsyncSession):
        self.session = session

    async def get_by_barcode(self, barcode: str) -> Optional[Product]:
        stmt = (
            select(DBProduct)
            .options(
                selectinload(DBProduct.ingredients)
                .selectinload(DBProductIngredient.ingredient)
                .selectinload(DBIngredient.names),
                selectinload(DBProduct.ingredients)
                .selectinload(DBProductIngredient.ingredient)
                .selectinload(DBIngredient.bans)
            )
            .where(DBProduct.barcode == barcode)
        )
        res = await self.session.execute(stmt)
        db_prod = res.scalar_one_or_none()
        return to_domain_product(db_prod) if db_prod else None

    async def search_by_name(self, query: str) -> List[Product]:
        stmt = (
            select(DBProduct)
            .options(
                selectinload(DBProduct.ingredients)
                .selectinload(DBProductIngredient.ingredient)
                .selectinload(DBIngredient.names),
                selectinload(DBProduct.ingredients)
                .selectinload(DBProductIngredient.ingredient)
                .selectinload(DBIngredient.bans)
            )
        )
        if query:
            stmt = stmt.where(
                or_(
                    DBProduct.name.ilike(f"%{query}%"),
                    DBProduct.brand.ilike(f"%{query}%")
                )
            )
        res = await self.session.execute(stmt)
        db_prods = res.scalars().all()
        return [to_domain_product(p) for p in db_prods]

    async def search_by_ingredient_names(self, names: List[str]) -> List[Tuple[Product, float]]:
        cleaned_names = [n.strip() for n in names if n and n.strip()]
        if not cleaned_names:
            return []

        # Resolve ingredient ids matching any of the provided names, either directly
        # against the ingredient's primary name or via its synonym table.
        direct_conditions = [DBIngredient.name.ilike(f"%{n}%") for n in cleaned_names]
        stmt_direct = select(DBIngredient.id).where(or_(*direct_conditions))
        res = await self.session.execute(stmt_direct)
        ingredient_ids = set(res.scalars().all())

        synonym_conditions = [DBIngredientName.name.ilike(f"%{n}%") for n in cleaned_names]
        stmt_syn = select(DBIngredientName.ingredient_id).where(or_(*synonym_conditions))
        res = await self.session.execute(stmt_syn)
        ingredient_ids.update(res.scalars().all())

        if not ingredient_ids:
            return []

        # Reuse search_by_name("") to load the full (small) product catalog with
        # ingredients/bans eager-loaded, then intersect ingredient-id sets in Python.
        # This mirrors the same ratio semantics ScanImageUseCase used to compute inline.
        all_products = await self.search_by_name("")
        results: List[Tuple[Product, float]] = []
        for p in all_products:
            p_ing_ids = {pi.ingredient.id for pi in p.ingredients}
            if not p_ing_ids:
                continue
            intersect = p_ing_ids.intersection(ingredient_ids)
            if intersect:
                ratio = len(intersect) / max(len(p_ing_ids), 1)
                results.append((p, ratio))

        results.sort(key=lambda r: r[1], reverse=True)
        return results

    async def save(self, product: Product) -> Product:
        # Check if already exists by barcode
        stmt = select(DBProduct).where(DBProduct.barcode == product.barcode)
        res = await self.session.execute(stmt)
        db_prod = res.scalar_one_or_none()

        if not db_prod:
            db_prod = DBProduct(
                name=product.name,
                brand=product.brand,
                barcode=product.barcode,
                category=product.category,
                source=product.source
            )
            self.session.add(db_prod)
            await self.session.flush()
        else:
            db_prod.name = product.name
            db_prod.brand = product.brand
            db_prod.category = product.category
            db_prod.source = product.source
            # Clear old ingredients mapping
            stmt_del = select(DBProductIngredient).where(DBProductIngredient.product_id == db_prod.id)
            res_del = await self.session.execute(stmt_del)
            for pi in res_del.scalars().all():
                await self.session.delete(pi)
            await self.session.flush()

        # Add ingredients mapping
        for pi in product.ingredients:
            # Ensure the ingredient exists
            ing_stmt = select(DBIngredient).where(DBIngredient.id == pi.ingredient.id)
            ing_res = await self.session.execute(ing_stmt)
            db_ing = ing_res.scalar_one_or_none()

            if not db_ing:
                db_ing = DBIngredient(
                    id=pi.ingredient.id,
                    name=pi.ingredient.name,
                    category=pi.ingredient.category,
                    e_number=pi.ingredient.e_number,
                    inci_name=pi.ingredient.inci_name,
                    cas_number=pi.ingredient.cas_number
                )
                self.session.add(db_ing)
                await self.session.flush()

            db_pi = DBProductIngredient(
                product_id=db_prod.id,
                ingredient_id=db_ing.id,
                order=pi.order
            )
            self.session.add(db_pi)
        
        await self.session.commit()
        # Retrieve freshly saved product with relationships loaded
        return await self.get_by_barcode(product.barcode)


class SQLAlchemyIngredientRepository(IIngredientRepository):
    def __init__(self, session: AsyncSession):
        self.session = session

    async def get_by_id(self, ingredient_id: str) -> Optional[Ingredient]:
        stmt = (
            select(DBIngredient)
            .options(
                selectinload(DBIngredient.names),
                selectinload(DBIngredient.bans)
            )
            .where(DBIngredient.id == ingredient_id)
        )
        res = await self.session.execute(stmt)
        db_ing = res.scalar_one_or_none()
        return to_domain_ingredient(db_ing) if db_ing else None

    async def search_ingredients(self, query: str, limit: int = 10) -> List[Ingredient]:
        cleaned = (query or "").strip()
        if not cleaned:
            return []
        cleaned_lower = cleaned.lower()
        like_pattern = f"%{cleaned}%"

        def rank_for(text: Optional[str]) -> int:
            # 0 = exact match, 1 = prefix match, 2 = substring match, 3 = no match
            if not text:
                return 3
            t = text.lower()
            if t == cleaned_lower:
                return 0
            if t.startswith(cleaned_lower):
                return 1
            if cleaned_lower in t:
                return 2
            return 3

        candidate_ranks: dict = {}

        # Direct matches against the ingredient's own name/e_number
        stmt_direct = select(DBIngredient.id, DBIngredient.name, DBIngredient.e_number).where(
            or_(
                DBIngredient.name.ilike(like_pattern),
                DBIngredient.e_number.ilike(like_pattern)
            )
        )
        res = await self.session.execute(stmt_direct)
        for ing_id, name, e_number in res.all():
            rank = min(rank_for(name), rank_for(e_number))
            candidate_ranks[ing_id] = min(candidate_ranks.get(ing_id, 3), rank)

        # Matches against the synonym table
        stmt_syn = select(DBIngredientName.ingredient_id, DBIngredientName.name).where(
            DBIngredientName.name.ilike(like_pattern)
        )
        res = await self.session.execute(stmt_syn)
        for ing_id, name in res.all():
            rank = rank_for(name)
            candidate_ranks[ing_id] = min(candidate_ranks.get(ing_id, 3), rank)

        if not candidate_ranks:
            return []

        ranked_ids = sorted(candidate_ranks.keys(), key=lambda i: candidate_ranks[i])[:limit]

        # Eager-load full ingredients (names + bans), same as get_by_id, preserving rank order
        stmt = (
            select(DBIngredient)
            .options(
                selectinload(DBIngredient.names),
                selectinload(DBIngredient.bans)
            )
            .where(DBIngredient.id.in_(ranked_ids))
        )
        res = await self.session.execute(stmt)
        db_ings = {ing.id: ing for ing in res.scalars().all()}
        return [to_domain_ingredient(db_ings[i]) for i in ranked_ids if i in db_ings]

    async def find_by_name_or_synonym(self, name: str) -> Optional[Ingredient]:
        if not name:
            return None
        cleaned_name = name.strip().lower()

        # Group direct ID, name (lower), and E-number checks into a single query
        conditions = [
            DBIngredient.id == cleaned_name,
            func.lower(DBIngredient.name) == cleaned_name
        ]
        
        e_match = re.search(r'(?i)\be[- ]?(\d+[a-z]?)\b', cleaned_name)
        if e_match:
            conditions.append(DBIngredient.e_number == f"E{e_match.group(1).upper()}")

            
        stmt = select(DBIngredient).where(or_(*conditions))
        res = await self.session.execute(stmt)
        db_ing = res.scalar_one_or_none()
        if db_ing:
            return await self.get_by_id(db_ing.id)

        # If not found, try by exact name match in multilingual translations (synonyms)
        stmt = (
            select(DBIngredientName)
            .where(func.lower(DBIngredientName.name) == cleaned_name)
        )
        res = await self.session.execute(stmt)
        db_name = res.scalars().first()
        if db_name:
            return await self.get_by_id(db_name.ingredient_id)

        # Fallback: partial match lookup
        stmt = (
            select(DBIngredientName)
            .where(DBIngredientName.name.ilike(f"%{cleaned_name}%"))
        )
        res = await self.session.execute(stmt)
        db_names = res.scalars().all()
        if db_names:
            return await self.get_by_id(db_names[0].ingredient_id)

        return None


    async def save(self, ingredient: Ingredient) -> Ingredient:
        stmt = select(DBIngredient).where(DBIngredient.id == ingredient.id)
        res = await self.session.execute(stmt)
        db_ing = res.scalar_one_or_none()

        if not db_ing:
            db_ing = DBIngredient(
                id=ingredient.id,
                name=ingredient.name,
                category=ingredient.category,
                e_number=ingredient.e_number,
                inci_name=ingredient.inci_name,
                cas_number=ingredient.cas_number
            )
            self.session.add(db_ing)
            await self.session.flush()
        else:
            db_ing.name = ingredient.name
            db_ing.category = ingredient.category
            db_ing.e_number = ingredient.e_number
            db_ing.inci_name = ingredient.inci_name
            db_ing.cas_number = ingredient.cas_number
            
            # Clear old translations and bans
            stmt_n = select(DBIngredientName).where(DBIngredientName.ingredient_id == db_ing.id)
            res_n = await self.session.execute(stmt_n)
            for n in res_n.scalars().all():
                await self.session.delete(n)

            stmt_b = select(DBIngredientBan).where(DBIngredientBan.ingredient_id == db_ing.id)
            res_b = await self.session.execute(stmt_b)
            for b in res_b.scalars().all():
                await self.session.delete(b)
            await self.session.flush()

        # Add new names
        for name in ingredient.names:
            db_name = DBIngredientName(
                ingredient_id=db_ing.id,
                lang=name.lang,
                name=name.name
            )
            self.session.add(db_name)

        # Add new bans
        for ban in ingredient.bans:
            db_ban = DBIngredientBan(
                ingredient_id=db_ing.id,
                country_code=ban.country_code,
                status=ban.status,
                condition=ban.condition,
                regulation_ref=ban.regulation_ref,
                effective_date=ban.effective_date
            )
            self.session.add(db_ban)

        await self.session.commit()
        return await self.get_by_id(ingredient.id)
