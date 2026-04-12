from fastapi import APIRouter, UploadFile, File, HTTPException, status
from typing import Dict
from ..utils.preprocessing import extract_features_from_bytes
from ..utils.pipelines import load_pipeline
import os

router = APIRouter()

# load model once
MODEL = "model/pipeline_model.pkl"
try:
    MODEL = load_pipeline()
except Exception as e:
    # keep None: main.py akan memeriksa dan melaporkan
    MODEL = None
    _LOAD_ERR = str(e)

@router.post("/predict", status_code=200)
async def predict(file: UploadFile = File(...)) -> Dict:
    if MODEL is None:
        raise HTTPException(status_code=500, detail=f"Model belum diload. {_LOAD_ERR}")

    if not file.filename:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="File tidak diberikan")

    try:
        content = await file.read()
        features = extract_features_from_bytes(content)  # shape (1,20)
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Error mengolah gambar: {e}")

    # sanity shape
    if features.ndim != 2 or features.shape[1] != 20:
        raise HTTPException(status_code=400, detail=f"Fitur tidak sesuai shape: {features.shape}")

    try:
        probs = MODEL.predict_proba(features)[0]
        classes = MODEL.classes_
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Model predict error: {e}")

    idx_sorted = probs.argsort()[::-1]
    top1 = str(classes[idx_sorted[0]])
    top2 = str(classes[idx_sorted[1]]) if len(classes) > 1 else None

    return {
        "top1_label": top1,
        "top2_label": top2,
    }