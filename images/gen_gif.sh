#!/usr/bin/env bash

# Generate GIFs of a screen recording preserving visual quality.
#
# Get gifski at https://github.com/ImageOptim/gifski

input="${1:-screen.mp4}"
output="${2:-anim.gif}"
# Desired GIF FPS
declare -i fps="${3:-50}"
# Include at most N frames
declare -i max_frames="${4:-620}"

ffmpeg \
    -i "$input" \
    -vf fps=$fps,select="lte(n\, $max_frames)" \
    -f yuv4mpegpipe - | \
        gifski \
            -o "$output" \
            --fps $fps \
            --lossy-quality 70 \
            --motion-quality 80 \
            --quality 80 \
            --width 720 \
            --height 1280 -
