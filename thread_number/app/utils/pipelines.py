import os
import joblib

DEFAULT_MODEL_PATH = os.path.join(os.path.dirname(__file__), "..", "models", "pipeline_model.pkl")
DEFAULT_MODEL_PATH = os.path.abspath(DEFAULT_MODEL_PATH)

def load_pipeline(model_path: str = None):
    path = model_path or DEFAULT_MODEL_PATH
    if not os.path.exists(path):
        raise FileNotFoundError(f"Model pipeline tidak ditemukan: {path}")
    model = joblib.load(path)
    return model
