import cv2
import numpy as np
import onnxruntime as ort
import sys

MODEL_PATH = "/Users/raju/Documents/edgeai/Chromis/app/src/main/assets/ddcolor-tiny-fp16.onnx"
INPUT_PATH = "/Users/raju/Downloads/boy.jpg"
OUTPUT_PATH = "/Users/raju/Downloads/boy_colorized.png"

print("Loading model...")
session = ort.InferenceSession(MODEL_PATH)

input_name = session.get_inputs()[0].name
output_name = session.get_outputs()[0].name
print(f"Input:  name={input_name}, shape={session.get_inputs()[0].shape}, dtype={session.get_inputs()[0].type}")
print(f"Output: name={output_name}, shape={session.get_outputs()[0].shape}, dtype={session.get_outputs()[0].type}")

print(f"\nLoading image: {INPUT_PATH}")
img = cv2.imread(INPUT_PATH)
if img is None:
    print(f"ERROR: Could not load {INPUT_PATH}")
    sys.exit(1)

print(f"Image shape: {img.shape}")
h, w = img.shape[:2]

# Convert BGR to RGB
img_rgb = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)

# Convert RGB to Lab
img_lab = cv2.cvtColor(img_rgb, cv2.COLOR_RGB2Lab)

# Extract L channel
l_channel = img_lab[:, :, 0]

# Resize L to 512x512
l_resized = cv2.resize(l_channel, (512, 512), interpolation=cv2.INTER_AREA)

# Prepare input: stack L 3 times, normalize to [0, 1], shape [1, 3, 512, 512]
l_float = l_resized.astype(np.float32) / 255.0
input_tensor = np.stack([l_float, l_float, l_float], axis=0)  # [3, 512, 512]
input_tensor = input_tensor[np.newaxis, ...]  # [1, 3, 512, 512]

print(f"\nInput tensor shape: {input_tensor.shape}")
print(f"Input range: [{input_tensor.min():.4f}, {input_tensor.max():.4f}]")
print(f"Input mean: {input_tensor.mean():.4f}")

# Run inference
print("\nRunning inference...")
result = session.run([output_name], {input_name: input_tensor})
ab_output = result[0]  # [1, 2, 512, 512]

print(f"Output shape: {ab_output.shape}")
print(f"Output range: [{ab_output.min():.4f}, {ab_output.max():.4f}]")
print(f"Output mean: {ab_output.mean():.4f}")
print(f"Output NaN count: {np.isnan(ab_output).sum()}")
print(f"Output Inf count: {np.isinf(ab_output).sum()}")

# Print a few sample values
for r in [0, 128, 256, 384, 511]:
    for c in [0, 128, 256, 384, 511]:
        a_val = ab_output[0, 0, r, c]
        b_val = ab_output[0, 1, r, c]
        print(f"  [{r},{c}] a={a_val:.6f} b={b_val:.6f}")

# Extract a and b channels
a_pred = ab_output[0, 0]  # [512, 512]
b_pred = ab_output[0, 1]  # [512, 512]

# Handle NaN: replace with 0 (neutral)
a_pred = np.nan_to_num(a_pred, nan=0.0)
b_pred = np.nan_to_num(b_pred, nan=0.0)

# Method 1: [-1, 1] -> [0, 255]
a_255_m1 = ((a_pred + 1.0) / 2.0 * 255.0).clip(0, 255).astype(np.uint8)
b_255_m1 = ((b_pred + 1.0) / 2.0 * 255.0).clip(0, 255).astype(np.uint8)

# Method 2: direct * 255 (what original code did)
a_255_m2 = (a_pred * 255.0).clip(0, 255).astype(np.uint8)
b_255_m2 = (b_pred * 255.0).clip(0, 255).astype(np.uint8)

# Method 3: add 128 (assuming output is Lab-centered around 0)
a_255_m3 = (a_pred + 128.0).clip(0, 255).astype(np.uint8)
b_255_m3 = (b_pred + 128.0).clip(0, 255).astype(np.uint8)

# Method 4: output * 127.5 + 128 (DDColor standard denormalization)
a_255_m4 = (a_pred * 127.5 + 128.0).clip(0, 255).astype(np.uint8)
b_255_m4 = (b_pred * 127.5 + 128.0).clip(0, 255).astype(np.uint8)

# Resize predicted a/b back to original size
a_high_m1 = cv2.resize(a_255_m1, (w, h), interpolation=cv2.INTER_AREA)
b_high_m1 = cv2.resize(b_255_m1, (w, h), interpolation=cv2.INTER_AREA)

a_high_m2 = cv2.resize(a_255_m2, (w, h), interpolation=cv2.INTER_AREA)
b_high_m2 = cv2.resize(b_255_m2, (w, h), interpolation=cv2.INTER_AREA)

a_high_m3 = cv2.resize(a_255_m3, (w, h), interpolation=cv2.INTER_AREA)
b_high_m3 = cv2.resize(b_255_m3, (w, h), interpolation=cv2.INTER_AREA)

a_high_m4 = cv2.resize(a_255_m4, (w, h), interpolation=cv2.INTER_AREA)
b_high_m4 = cv2.resize(b_255_m4, (w, h), interpolation=cv2.INTER_AREA)

# Reconstruct Lab image with original L + predicted a,b
# Method 1
lab_m1 = np.stack([l_channel, a_high_m1, b_high_m1], axis=-1)
rgb_m1 = cv2.cvtColor(lab_m1, cv2.COLOR_Lab2RGB)
cv2.imwrite(OUTPUT_PATH, cv2.cvtColor(rgb_m1, cv2.COLOR_RGB2BGR))
print(f"\nMethod 1 ([-1,1]->[0,255]) saved to: {OUTPUT_PATH}")

# Method 2
output_m2 = OUTPUT_PATH.replace(".png", "_m2.png")
lab_m2 = np.stack([l_channel, a_high_m2, b_high_m2], axis=-1)
rgb_m2 = cv2.cvtColor(lab_m2, cv2.COLOR_Lab2RGB)
cv2.imwrite(output_m2, cv2.cvtColor(rgb_m2, cv2.COLOR_RGB2BGR))
print(f"Method 2 (*255) saved to: {output_m2}")

# Method 3
output_m3 = OUTPUT_PATH.replace(".png", "_m3.png")
lab_m3 = np.stack([l_channel, a_high_m3, b_high_m3], axis=-1)
rgb_m3 = cv2.cvtColor(lab_m3, cv2.COLOR_Lab2RGB)
cv2.imwrite(output_m3, cv2.cvtColor(rgb_m3, cv2.COLOR_RGB2BGR))
print(f"Method 3 (+128) saved to: {output_m3}")

# Method 4
output_m4 = OUTPUT_PATH.replace(".png", "_m4.png")
lab_m4 = np.stack([l_channel, a_high_m4, b_high_m4], axis=-1)
rgb_m4 = cv2.cvtColor(lab_m4, cv2.COLOR_Lab2RGB)
cv2.imwrite(output_m4, cv2.cvtColor(rgb_m4, cv2.COLOR_RGB2BGR))
print(f"Method 4 (*127.5+128) saved to: {output_m4}")

# Also save input for reference
input_out = OUTPUT_PATH.replace(".png", "_input.png")
cv2.imwrite(input_out, img)
print(f"Input saved to: {input_out}")

print("\nDone!")
