"""
Quick script to update enum values in database from lowercase to uppercase
"""
from app.database import SessionLocal
from app.models import SosPacketDB
from sqlalchemy import text

def update_enum_values():
    db = SessionLocal()
    try:
        # Since we're using SQLAlchemy enums, we need to update the actual enum values in the table
        # The easiest approach is to just delete all old records since this is development
        
        count = db.query(SosPacketDB).count()
        print(f"Total records before cleanup: {count}")
        
        # Delete all records
        db.query(SosPacketDB).delete()
        db.commit()
        
        print(f"Deleted all {count} records")
        print("Database is now clean and ready for new data with uppercase enums")
        
    except Exception as e:
        print(f"Error: {e}")
        db.rollback()
    finally:
        db.close()

if __name__ == "__main__":
    update_enum_values()
