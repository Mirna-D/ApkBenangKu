from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from .routers import predict as predict_router
import uvicorn
import os
import logging

logging.basicConfig(level=logging.INFO)

app = FastAPI(title="Thread Number - SVM Inference")

# -------------------------------------------------
# CORS
# -------------------------------------------------
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # DEV ONLY
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# -------------------------------------------------
# ROUTER
# -------------------------------------------------
app.include_router(predict_router.router)

# -------------------------------------------------
# HEALTH CHECK
# -------------------------------------------------
@app.get("/")
def root():
    return {
        "service": "thread-number fastapi",
        "status": "ok"
    }

# -------------------------------------------------
# ENTRY POINT
# -------------------------------------------------
if __name__ == "__main__":
    uvicorn.run(
        "app.main:app",
        host="0.0.0.0",
        port=int(os.environ.get("PORT", 8000)),
        reload=False
    )
