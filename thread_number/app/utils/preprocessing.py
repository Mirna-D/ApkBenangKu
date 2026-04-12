import cv2
import numpy as np

IMG_SIZE = (128, 128)

def extract_gabor_features(gray):
    """
    Ekstraksi Gabor features: mengembalikan array panjang 8
    """
    features = []
    num_orientations = 4
    for theta in range(num_orientations):
        theta_val = theta / num_orientations * np.pi
        kernel = cv2.getGaborKernel((7,7), 4.0, theta_val, 10.0, 0.5, 0, ktype=cv2.CV_32F)
        fimg = cv2.filter2D(gray, cv2.CV_8UC3, kernel)
        # mean dan std dari hasil filter
        features.append(np.mean(fimg))
        features.append(np.std(fimg))
    return np.array(features, dtype=np.float32)

def extract_feature_vector_from_image(img_bgr):
    """
    Input: image dalam format BGR (numpy array dari OpenCV)
    Output: 1x20 numpy array (float32)
    """
    if img_bgr is None:
        raise ValueError("Input image is None")

    img = cv2.resize(img_bgr, IMG_SIZE)

    # Convert color spaces (training pakai RGB sebagai basis)
    img_rgb = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
    img_hsv = cv2.cvtColor(img_rgb, cv2.COLOR_RGB2HSV)
    img_lab = cv2.cvtColor(img_rgb, cv2.COLOR_RGB2LAB)
    img_hsl = cv2.cvtColor(img_rgb, cv2.COLOR_RGB2HLS)
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)

    # mean per channel
    rgb_mean = np.mean(img_rgb.reshape(-1, 3), axis=0)
    hsv_mean = np.mean(img_hsv.reshape(-1, 3), axis=0)
    lab_mean = np.mean(img_lab.reshape(-1, 3), axis=0)
    hsl_mean = np.mean(img_hsl.reshape(-1, 3), axis=0)

    gabor_feats = extract_gabor_features(gray)  # length 8

    feature_vector = np.concatenate([
        rgb_mean,     # 3
        hsv_mean,     # 3
        hsl_mean,     # 3
        lab_mean,     # 3
        gabor_feats   # 8
    ]).astype(np.float32)

    # bentuk (1,20)
    return feature_vector.reshape(1, -1)

def extract_features_from_bytes(image_bytes):
    """
    Decode bytes -> call extract_feature_vector_from_image
    """
    npimg = np.frombuffer(image_bytes, np.uint8)
    img = cv2.imdecode(npimg, cv2.IMREAD_COLOR)
    if img is None:
        raise ValueError("Gagal decode gambar dari bytes")
    return extract_feature_vector_from_image(img)
