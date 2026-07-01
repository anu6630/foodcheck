from sqlalchemy import Table, Column, Integer, String, ForeignKey, Date, Index
from sqlalchemy.orm import declarative_base, relationship

Base = declarative_base()

class DBCountry(Base):
    __tablename__ = "countries"
    
    code = Column(String(5), primary_key=True)
    name = Column(String(100), nullable=False)
    
    bans = relationship("DBIngredientBan", back_populates="country")

class DBIngredient(Base):
    __tablename__ = "ingredients"
    
    id = Column(String(100), primary_key=True)  # unique slug (e.g. "titanium-dioxide")
    name = Column(String(255), nullable=False)
    category = Column(String(100), nullable=True)
    e_number = Column(String(20), nullable=True, index=True)
    inci_name = Column(String(255), nullable=True)
    cas_number = Column(String(50), nullable=True)
    
    names = relationship("DBIngredientName", back_populates="ingredient", cascade="all, delete-orphan")
    bans = relationship("DBIngredientBan", back_populates="ingredient", cascade="all, delete-orphan")
    products = relationship("DBProductIngredient", back_populates="ingredient")

class DBIngredientName(Base):
    __tablename__ = "ingredient_names"
    
    id = Column(Integer, primary_key=True, autoincrement=True)
    ingredient_id = Column(String(100), ForeignKey("ingredients.id", ondelete="CASCADE"), nullable=False)
    lang = Column(String(5), nullable=False)
    name = Column(String(255), nullable=False, index=True)
    
    ingredient = relationship("DBIngredient", back_populates="names")

class DBIngredientBan(Base):
    __tablename__ = "ingredient_bans"
    
    id = Column(Integer, primary_key=True, autoincrement=True)
    ingredient_id = Column(String(100), ForeignKey("ingredients.id", ondelete="CASCADE"), nullable=False)
    country_code = Column(String(5), ForeignKey("countries.code", ondelete="CASCADE"), nullable=False)
    status = Column(String(20), nullable=False)  # "banned" | "restricted" | "permitted"
    condition = Column(String(500), nullable=True)
    regulation_ref = Column(String(255), nullable=True)
    effective_date = Column(Date, nullable=True)
    
    ingredient = relationship("DBIngredient", back_populates="bans")
    country = relationship("DBCountry", back_populates="bans")

class DBProduct(Base):
    __tablename__ = "products"
    
    id = Column(Integer, primary_key=True, autoincrement=True)
    name = Column(String(255), nullable=False)
    brand = Column(String(255), nullable=True)
    barcode = Column(String(50), unique=True, index=True, nullable=False)
    category = Column(String(100), nullable=True)
    source = Column(String(50), nullable=False)  # "local" | "open_food_facts"
    
    ingredients = relationship("DBProductIngredient", back_populates="product", cascade="all, delete-orphan")

class DBProductIngredient(Base):
    __tablename__ = "product_ingredients"
    
    id = Column(Integer, primary_key=True, autoincrement=True)
    product_id = Column(Integer, ForeignKey("products.id", ondelete="CASCADE"), nullable=False)
    ingredient_id = Column(String(100), ForeignKey("ingredients.id", ondelete="CASCADE"), nullable=False)
    order = Column(Integer, nullable=False)
    
    product = relationship("DBProduct", back_populates="ingredients")
    ingredient = relationship("DBIngredient", back_populates="products")

# Indexes for fast search
Index("idx_ingredient_names_name_lower", DBIngredientName.name)
Index("idx_ingredients_name_lower", DBIngredient.name)
Index("idx_products_name_lower", DBProduct.name)
