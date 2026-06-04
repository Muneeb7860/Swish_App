// main.py for FastAPI Order Service
// Deprecated FastAPI implementation – replaced by Java Spring Boot service

from pydantic import BaseModel

app = FastAPI()

class Order(BaseModel):
    id: int
    item: str
    quantity: int
    status: str = "pending"

# In‑memory store for demo purposes
orders = []

@app.get("/orders")
async def list_orders():
    return orders

@app.post("/orders")
async def create_order(order: Order):
    orders.append(order.dict())
    return {"message": "order created", "order": order}
