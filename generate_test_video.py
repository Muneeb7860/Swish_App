import os
import sys
import numpy as np
from PIL import Image, ImageDraw

# Run script using imageio and Pillow in our venv
import imageio

width, height = 640, 360
fps = 30
duration_seconds = 5
total_frames = fps * duration_seconds

# Output video path
video_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'test_video.mp4')

print(f"Generating test video: {width}x{height}, {fps} fps, {duration_seconds}s...")

writer = imageio.get_writer(video_path, fps=fps)

for frame_idx in range(total_frames):
    # Nice smooth gradient background cycle
    r = int(20 + 40 * np.sin(2 * np.pi * frame_idx / total_frames))
    g = int(24 + 40 * np.sin(2 * np.pi * frame_idx / total_frames + 2*np.pi/3))
    b = int(40 + 40 * np.sin(2 * np.pi * frame_idx / total_frames + 4*np.pi/3))
    
    img = Image.new("RGB", (width, height), (r, g, b))
    draw = ImageDraw.Draw(img)
    
    # Draw rotating circle/ellipse (representing a dynamic AI/Rider marker)
    angle = 2 * np.pi * frame_idx / fps  # 1 full rotation every second
    radius = 50
    cx = width // 2 + int(120 * np.cos(angle))
    cy = height // 2 + int(60 * np.sin(angle))
    
    # Red circle with white outline
    draw.ellipse([cx - radius, cy - radius, cx + radius, cy + radius], fill=(219, 39, 119), outline=(255, 255, 255), width=4)
    
    # Draw Swiss cross inside the circle representing the Swiss Q-Commerce brand
    draw.rectangle([cx - 6, cy - 22, cx + 6, cy + 22], fill=(255, 255, 255))
    draw.rectangle([cx - 22, cy - 6, cx + 22, cy + 6], fill=(255, 255, 255))
    
    # Draw grid lines for high-tech telemetry feeling
    for x in range(0, width, 40):
        draw.line([x, 0, x, height], fill=(255, 255, 255, 30), width=1)
    for y in range(0, height, 40):
        draw.line([0, y, width, y], fill=(255, 255, 255, 30), width=1)
        
    # Draw text banner
    draw.text((40, 40), "SWISH Q-COMMERCE ARCHITECTURE", fill=(100, 210, 255))
    
    # Draw countdown
    countdown = duration_seconds - (frame_idx // fps)
    draw.text((40, 60), f"Edge Proxy SSE & Telemetry Live Sync: {countdown}s", fill=(255, 255, 255))
    
    # Radar sweep line from center
    sweep_angle = angle * 1.5
    sx = width // 2 + int(180 * np.cos(sweep_angle))
    sy = height // 2 + int(180 * np.sin(sweep_angle))
    draw.line([width // 2, height // 2, sx, sy], fill=(16, 185, 129), width=2)
    
    # Center core (dark store MFC node)
    draw.ellipse([width // 2 - 15, height // 2 - 15, width // 2 + 15, height // 2 + 15], fill=(59, 130, 246), outline=(255, 255, 255), width=2)

    frame_data = np.array(img)
    writer.append_data(frame_data)

writer.close()
print(f"Test video created successfully at: {video_path}")
