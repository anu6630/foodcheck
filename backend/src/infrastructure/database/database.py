import os
import logging
from urllib.parse import urlparse, urlunparse, unquote
import psycopg2
from sqlalchemy.ext.asyncio import create_async_engine, AsyncSession
from sqlalchemy.orm import sessionmaker


logger = logging.getLogger(__name__)

# Default URL pointing to the postgres database to check and create foodcheck
DEFAULT_RAW_URL = os.getenv("DATABASE_URL", "postgresql://postgres.mhnreqfbohczronjprzs:Plmokn%40123%24%24@aws-1-ap-northeast-2.pooler.supabase.com:5432/postgres")

def get_connection_urls():
    """
    Parses the DATABASE_URL and creates:
    1. A sync postgres connection URL (for setup/checks)
    2. An async connection URL (for FastAPI/SQLAlchemy)
    """
    raw_url = os.getenv("DATABASE_URL", DEFAULT_RAW_URL)
    
    # Clean up standard postgres/postgresql protocols
    if raw_url.startswith("postgres://"):
        raw_url = raw_url.replace("postgres://", "postgresql://", 1)
        
    # Create the setup url pointing to the administrative 'postgres' db
    parsed = urlparse(raw_url)
    
    # 1. Setup URL (sync connection targeting 'postgres' db)
    setup_parsed = parsed._replace(path="/postgres")
    sync_setup_url = urlunparse(setup_parsed)
    
    # 2. Project URL (async connection targeting 'foodcheck' db)
    # Ensure it ends with '/foodcheck' database name
    proj_parsed = parsed._replace(path="/foodcheck")
    sync_project_url = urlunparse(proj_parsed)
    async_project_url = sync_project_url.replace("postgresql://", "postgresql+asyncpg://", 1)
    
    return sync_setup_url, async_project_url

SYNC_SETUP_URL, ASYNC_DATABASE_URL = get_connection_urls()

# Setup async SQLAlchemy engine
engine = create_async_engine(
    ASYNC_DATABASE_URL,
    pool_pre_ping=True,
    pool_size=5,
    max_overflow=10
)

AsyncSessionLocal = sessionmaker(
    bind=engine,
    class_=AsyncSession,
    expire_on_commit=False
)

def ensure_database_exists():
    """
    Connects to 'postgres' database synchronously via psycopg2
    and runs CREATE DATABASE foodcheck if not exists.
    """
    try:
        parsed = urlparse(SYNC_SETUP_URL)
        conn = psycopg2.connect(
            dbname="postgres",
            user=unquote(parsed.username) if parsed.username else None,
            password=unquote(parsed.password) if parsed.password else None,
            host=parsed.hostname,
            port=parsed.port or 5432
        )
        conn.autocommit = True
        cursor = conn.cursor()
        
        # Check if database exists
        cursor.execute("SELECT 1 FROM pg_database WHERE datname = 'foodcheck';")
        exists = cursor.fetchone()
        
        if not exists:
            logger.info("Database 'foodcheck' does not exist. Creating...")
            cursor.execute("CREATE DATABASE foodcheck;")
            logger.info("Database 'foodcheck' created successfully.")
        else:
            logger.info("Database 'foodcheck' already exists.")
            
        cursor.close()
        conn.close()
    except Exception as e:
        logger.warning(f"Error checking or creating database 'foodcheck': {e}. Proceeding using connection URL db.")
        # Do not raise the exception so we can fall back to using default target db


async def get_db_session():
    async with AsyncSessionLocal() as session:
        try:
            yield session
        except Exception:
            await session.rollback()
            raise
        finally:
            await session.close()
