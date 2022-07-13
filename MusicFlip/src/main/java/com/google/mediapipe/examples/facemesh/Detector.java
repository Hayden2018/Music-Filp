package com.google.mediapipe.examples.facemesh;

import android.opengl.Matrix;
import android.util.Log;

import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmark;

import java.util.List;

public class Detector {

    float[] transformMatrix = new float[16];
    float sensitivity = 0.8f;

    public Detector() {

    }

    private float[] transform(NormalizedLandmark lm) {
        float[] input = {lm.getX(), lm.getY(), lm.getZ(), 1};
        float[] result = new float[4];

        Matrix.multiplyMV(result, 0, transformMatrix, 0, input, 0);
        return result;
    }

    public void setTransformMatrix(List<NormalizedLandmark> landmarks) {

        // Base coordinate (Back-center of the face)
        float bx = (landmarks.get(93).getX() + landmarks.get(323).getX()) / 2;
        float by = (landmarks.get(93).getY() + landmarks.get(323).getY()) / 2;
        float bz = (landmarks.get(93).getZ() + landmarks.get(323).getZ()) / 2;

        // Reference vector (Vector from base to nose)
        float rx = landmarks.get(1).getX() - bx;
        float ry = landmarks.get(1).getY() - by;
        float rz = landmarks.get(1).getZ() - bz;
        float norm = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
        float scalar = 1 / norm;

        Matrix.setIdentityM(transformMatrix, 0);
        Matrix.translateM(transformMatrix, 0, -bx, -by, -bz);
        Matrix.scaleM(transformMatrix, 0, scalar, scalar, scalar);

        // k is the axis of rotation to rotate reference vector to (0, 0, -1)
        // theta is the angle of rotation
        float kx = -ry;
        float ky = rx;
        float kz = 0;
        float theta = (float) Math.acos(-rz * scalar);

        Matrix.rotateM(transformMatrix, 0, theta, kx, ky, kz);

        float[] top = transform(landmarks.get(10));

        // Up vector (Vector from base to face top)
        float ux = top[0];
        float uy = top[1];
        float uz = top[2];
        norm = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        scalar = 1 / norm;

        // k is the axis of rotation to rotate reference vector to (0, -1, 0)
        // theta is the angle of rotation
        kx = uz;
        ky = 0;
        kz = -ux;
        theta = (float) Math.acos(-uy * scalar);

        Matrix.rotateM(transformMatrix, 0, theta, kx, ky, kz);
    }

    public void setSensitivity(float s) {
        sensitivity = s;
    }

    final int[] rightEyeUpper = {398, 384, 385, 386, 387, 388, 466};
    final int[] rightEyeLower = {382, 381, 380, 374, 373, 390, 249};
    final int[] leftEyeUpper = {246, 161, 160, 159, 158, 157, 173};
    final int[] leftEyeLower = {7, 163, 144, 145, 153, 154, 155};

    public Enum<MainActivity.Blink> detectBlink(List<NormalizedLandmark> landmarks) {
        float right = 0;
        float left = 0;

        float[] top = transform(landmarks.get(164));
        float[] bottom = transform(landmarks.get(0));

        float tilt = bottom[0] - top[0];

        for (int i = 0; i < 7; ++i) {
            top = transform(landmarks.get(rightEyeUpper[i]));
            bottom = transform(landmarks.get(rightEyeLower[i]));
            right += bottom[1] - top[1];
            top = transform(landmarks.get(leftEyeUpper[i]));
            bottom = transform(landmarks.get(leftEyeLower[i]));
            left += bottom[1] - top[1];
        }

        float eyeThreshold = 0.18f - 0.1f * sensitivity;
        float tiltThreshold = 0.08f - 0.06f * sensitivity;

        if (right < 0.12 && left < 0.12) return MainActivity.Blink.NONE;

        if (right > (left + eyeThreshold) && tilt < -tiltThreshold) return MainActivity.Blink.LEFT;
        if (left > (right + eyeThreshold) && tilt > tiltThreshold) return MainActivity.Blink.RIGHT;
        return MainActivity.Blink.NONE;
    }

    public Enum<MainActivity.Shake> detectShake(List<NormalizedLandmark> landmarks) {

        // Base coordinate (Back-center of the face)
        float bx = (landmarks.get(93).getX() + landmarks.get(323).getX()) / 2;
        float bz = (landmarks.get(93).getZ() + landmarks.get(323).getZ()) / 2;

        // Reference vector (Vector from base to nose)
        float rx = landmarks.get(1).getX() - bx;
        float rz = landmarks.get(1).getZ() - bz;

        if (rx / rz > 0.3) return MainActivity.Shake.RIGHT;
        if (rx / rz < -0.3) return MainActivity.Shake.LEFT;
        return MainActivity.Shake.NONE;

    }
}
