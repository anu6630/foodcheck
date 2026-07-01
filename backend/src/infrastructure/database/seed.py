import os
import re
import csv
import asyncio
import logging
from datetime import date
from sqlalchemy import select, insert
from sqlalchemy.ext.asyncio import AsyncSession

# Setup logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

from src.infrastructure.database.database import ensure_database_exists, engine, AsyncSessionLocal
from src.adapters.models import Base, DBCountry, DBIngredient, DBIngredientName, DBIngredientBan, DBProduct, DBProductIngredient

# Map local name abbreviations in IFCT 2017 to ISO codes
LANG_MAP = {
    "H": "hi",    # Hindi
    "G": "gu",    # Gujarati
    "Kan": "kn",  # Kannada
    "Mal": "ml",  # Malayalam
    "Mar": "mr",  # Marathi
    "O": "or",    # Oriya
    "P": "pa",    # Punjabi
    "Tam": "ta",  # Tamil
    "Tel": "te",  # Telugu
    "Kash": "ks", # Kashmiri
    "A": "as",    # Assamese
    "B": "bn",    # Bengali
    "M": "mni",   # Manipuri
    "Kh": "kha",  # Khasi
    "N": "ne",    # Nepali
    "S": "sa",    # Sanskrit
    "U": "ur"     # Urdu
}

def parse_ifct_local_names(local_names_str: str):
    """
    Parses a string like: "A. Moricha guti; H. Ramdana; Kan. Danthu beeja"
    into a list of dicts: [{"lang": "hi", "name": "Ramdana"}, ...]
    """
    results = []
    if not local_names_str or local_names_str.strip() == "":
        return results
        
    parts = local_names_str.split(";")
    for part in parts:
        # Regex to match Language. LocalName
        match = re.match(r'^\s*([A-Za-z]+)\.\s*(.+)$', part.strip())
        if match:
            lang_abbrev = match.group(1)
            local_name = match.group(2).strip()
            iso_lang = LANG_MAP.get(lang_abbrev)
            if iso_lang:
                results.append({"lang": iso_lang, "name": local_name})
    return results

async def seed_data():
    logger.info("Starting database seeding...")
    
    # Step 1: Ensure the database exists in the cluster
    ensure_database_exists()
    
    # Step 2: Create all schemas
    async with engine.begin() as conn:
        logger.info("Recreating schemas...")
        await conn.run_sync(Base.metadata.drop_all)
        await conn.run_sync(Base.metadata.create_all)
        logger.info("Schemas created.")
        
    async with AsyncSessionLocal() as session:
        # Step 3: Seed Countries
        logger.info("Seeding countries...")
        countries = [
            DBCountry(code="IN", name="India"),
            DBCountry(code="EU", name="European Union"),
            DBCountry(code="US", name="United States"),
            DBCountry(code="AU", name="Australia")
        ]
        session.add_all(countries)
        await session.flush()
        
        # Step 4: Seed IFCT 2017 dataset
        csv_path = ".research/openfoodfacts-server/external-data/ifct/IndianFoodCompositionTables2017.csv"
        if os.path.exists(csv_path):
            logger.info(f"Parsing IFCT dataset from {csv_path}...")
            with open(csv_path, mode="r", encoding="utf-8-sig") as f:
                reader = csv.reader(f)
                header = next(reader)
                
                ingredients_to_insert = []
                names_to_insert = []
                
                for row in reader:
                    if not row or len(row) < 5:
                        continue
                    food_code = row[0].strip()
                    food_name = row[1].strip()
                    scientific_name = row[2].strip()
                    local_names_raw = row[3].strip()
                    food_group = row[4].strip()
                    
                    # Create unique slug ID
                    slug_id = f"ifct-{food_code.lower()}"
                    
                    ingredients_to_insert.append({
                        "id": slug_id,
                        "name": food_name,
                        "category": food_group,
                        "cas_number": None,
                        "inci_name": scientific_name if scientific_name else None,
                        "e_number": None
                    })
                    
                    # Add primary English name translation
                    names_to_insert.append({
                        "ingredient_id": slug_id,
                        "lang": "en",
                        "name": food_name
                    })
                    
                    # Add local language names
                    local_names = parse_ifct_local_names(local_names_raw)
                    for ln in local_names:
                        names_to_insert.append({
                            "ingredient_id": slug_id,
                            "lang": ln["lang"],
                            "name": ln["name"]
                        })
                
                # Bulk insert ingredients
                if ingredients_to_insert:
                    await session.execute(insert(DBIngredient), ingredients_to_insert)
                    await session.execute(insert(DBIngredientName), names_to_insert)
                    logger.info(f"Imported {len(ingredients_to_insert)} ingredients from IFCT 2017.")
        else:
            logger.warning(f"IFCT dataset not found at {csv_path}. Skipping.")

        # Step 5: Seed Additives from additives.txt
        additives_path = ".research/openfoodfacts-server/taxonomies/additives.txt"
        if os.path.exists(additives_path):
            logger.info(f"Parsing additives from {additives_path}...")
            with open(additives_path, mode="r", encoding="utf-8") as f:
                content = f.read()
                
            blocks = content.split("\n\n")
            logger.info(f"Found {len(blocks)} blocks in additives taxonomy.")
            
            ingredients_to_insert = []
            names_to_insert = []
            
            for block in blocks:
                block = block.strip()
                if not block or block.startswith("#") or block.startswith("synonyms") or block.startswith("stopwords"):
                    continue
                
                lines = block.split("\n")
                en_line = next((line for line in lines if line.startswith("en:")), None)
                if not en_line:
                    continue
                
                # Format: en: E100, Curcumin, Turmeric extract...
                # We strip the prefix "en:" and split by comma
                raw_names = en_line[3:].split(",")
                primary_e_number = raw_names[0].strip()
                
                # Check if it starts with E followed by digits (valid E-number format)
                if not (primary_e_number.startswith("E") and primary_e_number[1:].isdigit()):
                    # Fallback to check other names or skip if not an E-number
                    continue
                    
                e_number = primary_e_number
                slug_id = f"additive-{e_number.lower()}"
                primary_name = raw_names[1].strip() if len(raw_names) > 1 else e_number
                
                ingredients_to_insert.append({
                    "id": slug_id,
                    "name": primary_name,
                    "category": "additive",
                    "cas_number": None,
                    "inci_name": None,
                    "e_number": e_number
                })
                
                # Add all synonyms from en: line
                for rn in raw_names:
                    names_to_insert.append({
                        "ingredient_id": slug_id,
                        "lang": "en",
                        "name": rn.strip()
                    })
                
                # Check for other languages translations in this block (e.g. hi:)
                for line in lines:
                    if line.startswith("hi:"):
                        hi_names = line[3:].split(",")
                        for hn in hi_names:
                            names_to_insert.append({
                                "ingredient_id": slug_id,
                                "lang": "hi",
                                "name": hn.strip()
                            })
                            
            if ingredients_to_insert:
                # Filter out duplicates (some E-numbers might be defined multiple times in taxonomy)
                unique_ingredients = {i["id"]: i for i in ingredients_to_insert}.values()
                await session.execute(insert(DBIngredient), list(unique_ingredients))
                await session.execute(insert(DBIngredientName), names_to_insert)
                logger.info(f"Imported {len(unique_ingredients)} E-numbers from additives taxonomy.")
        else:
            logger.warning(f"Additives taxonomy not found at {additives_path}. Skipping.")

        # Step 6: Create or update targeted test ingredients (E171, E924, E102, E211)
        # to guarantee they are mapped and have proper regulatory ban entries
        logger.info("Setting up targeted regulatory ingredients and bans...")
        
        targeted_ingredients = [
            {
                "id": "additive-e171",
                "name": "Titanium Dioxide",
                "category": "colour",
                "e_number": "E171",
                "synonyms": ["Titanium Dioxide", "E171", "titanium oxide", "E 171", "ci 77891"],
                "bans": [
                    {"country_code": "EU", "status": "banned", "condition": None, "regulation_ref": "Commission Regulation EU 2022/63"},
                    {"country_code": "IN", "status": "permitted", "condition": "GMP limits", "regulation_ref": "FSSAI Food Additives Annexure"},
                    {"country_code": "US", "status": "permitted", "condition": "Max 1% by weight", "regulation_ref": "FDA 21 CFR 73.575"}
                ]
            },
            {
                "id": "additive-e924",
                "name": "Potassium Bromate",
                "category": "treatment agent",
                "e_number": "E924",
                "synonyms": ["Potassium Bromate", "E924", "potassium bromate (E924)", "bromate of potassium"],
                "bans": [
                    {"country_code": "IN", "status": "banned", "condition": "Banned as bread improvement agent since 2016", "regulation_ref": "FSSAI Order 2016"},
                    {"country_code": "EU", "status": "banned", "condition": "Carcinogenic hazard ban", "regulation_ref": "EU Directive 90/642/EEC"},
                    {"country_code": "AU", "status": "banned", "condition": "Not permitted in food standard", "regulation_ref": "FSANZ Standard 1.3.1"}
                ]
            },
            {
                "id": "additive-e102",
                "name": "Tartrazine",
                "category": "colour",
                "e_number": "E102",
                "synonyms": ["Tartrazine", "E102", "FD&C Yellow No. 5", "Yellow 5", "acid yellow 23"],
                "bans": [
                    {"country_code": "EU", "status": "restricted", "condition": "Must carry warning label: 'May have an adverse effect on activity and attention in children'", "regulation_ref": "EU Regulation 1333/2008 Annex V"},
                    {"country_code": "IN", "status": "restricted", "condition": "Maximum limit 100 mg/kg in permitted foods", "regulation_ref": "FSSAI Standard 2.4.1"}
                ]
            },
            {
                "id": "additive-e211",
                "name": "Sodium Benzoate",
                "category": "preservative",
                "e_number": "E211",
                "synonyms": ["Sodium Benzoate", "E211", "sodium salt of benzoic acid"],
                "bans": [
                    {"country_code": "IN", "status": "restricted", "condition": "Limit ranges from 50 to 600 ppm depending on category", "regulation_ref": "FSSAI Standard 3.1.1"},
                    {"country_code": "EU", "status": "restricted", "condition": "Maximum permitted level 150-2000 mg/kg", "regulation_ref": "EU Annex II 1333/2008"}
                ]
            }
        ]
        
        for ti in targeted_ingredients:
            # Check if exists
            stmt = select(DBIngredient).where(DBIngredient.id == ti["id"])
            res = await session.execute(stmt)
            db_ing = res.scalar_one_or_none()
            
            if not db_ing:
                db_ing = DBIngredient(
                    id=ti["id"],
                    name=ti["name"],
                    category=ti["category"],
                    e_number=ti["e_number"]
                )
                session.add(db_ing)
                await session.flush()
                
            # Upsert synonyms
            for syn in ti["synonyms"]:
                syn_stmt = select(DBIngredientName).where(
                    DBIngredientName.ingredient_id == db_ing.id,
                    DBIngredientName.name == syn
                )
                syn_res = await session.execute(syn_stmt)
                if not syn_res.scalars().first():
                    session.add(DBIngredientName(
                        ingredient_id=db_ing.id,
                        lang="en",
                        name=syn
                    ))
            
            # Upsert bans
            for ban in ti["bans"]:
                ban_stmt = select(DBIngredientBan).where(
                    DBIngredientBan.ingredient_id == db_ing.id,
                    DBIngredientBan.country_code == ban["country_code"]
                )
                db_ban = (await session.execute(ban_stmt)).scalars().first()
                if not db_ban:
                    session.add(DBIngredientBan(
                        ingredient_id=db_ing.id,
                        country_code=ban["country_code"],
                        status=ban["status"],
                        condition=ban["condition"],
                        regulation_ref=ban["regulation_ref"]
                    ))
                else:
                    db_ban.status = ban["status"]
                    db_ban.condition = ban["condition"]
                    db_ban.regulation_ref = ban["regulation_ref"]
                    
        await session.commit()
        logger.info("Targeted regulatory ingredients and bans seeded.")

        # Step 7: Seed sample products for tests
        logger.info("Seeding sample products...")
        sample_products = [
            {
                "name": "White Cookie Frosting",
                "brand": "Betty Crocker",
                "barcode": "016000452327",
                "category": "Frosting & Icings",
                "source": "local",
                "ingredients": [
                    {"id": "additive-e171", "order": 0},  # Titanium Dioxide E171
                    {"id": "additive-e211", "order": 1}   # Sodium Benzoate E211
                ]
            },
            {
                "name": "Sliced White Bread",
                "brand": "Golden Crust",
                "barcode": "111111111111",
                "category": "Bakery",
                "source": "local",
                "ingredients": [
                    {"id": "additive-e924", "order": 0},  # Potassium Bromate E924
                    {"id": "additive-e211", "order": 1}   # Sodium Benzoate E211
                ]
            }
        ]
        
        for sp in sample_products:
            stmt = select(DBProduct).where(DBProduct.barcode == sp["barcode"])
            res = await session.execute(stmt)
            db_p = res.scalar_one_or_none()
            
            if not db_p:
                db_p = DBProduct(
                    name=sp["name"],
                    brand=sp["brand"],
                    barcode=sp["barcode"],
                    category=sp["category"],
                    source=sp["source"]
                )
                session.add(db_p)
                await session.flush()
                
                # Link ingredients
                for ing_map in sp["ingredients"]:
                    # Verify ingredient exists
                    ing_stmt = select(DBIngredient).where(DBIngredient.id == ing_map["id"])
                    ing_res = await session.execute(ing_stmt)
                    db_ing = ing_res.scalar_one_or_none()
                    
                    if not db_ing:
                        # Fallback try checking targeted id if it was mapped as additives
                        # Create if doesn't exist
                        db_ing = DBIngredient(
                            id=ing_map["id"],
                            name=ing_map["id"].replace("-", " ").title(),
                            category="colour" if "e171" in ing_map["id"] else "additive"
                        )
                        session.add(db_ing)
                        await session.flush()
                        
                    session.add(DBProductIngredient(
                        product_id=db_p.id,
                        ingredient_id=db_ing.id,
                        order=ing_map["order"]
                    ))
                    
        await session.commit()
        logger.info("Sample products seeded successfully.")
        logger.info("Database seeding complete!")

if __name__ == "__main__":
    asyncio.run(seed_data())
