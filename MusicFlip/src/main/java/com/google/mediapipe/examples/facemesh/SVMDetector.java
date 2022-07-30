package com.google.mediapipe.examples.facemesh;

import android.content.res.Resources;
import android.opengl.Matrix;

import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmark;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.List;
import java.util.Scanner;

public class SVMDetector {

    float[] transformMatrix = new float[16];
    float sensitivity = 0.5f;

    float[] Np = new float[1434];
    float[] Rp = new float[1434];
    float[] Lp = new float[1434];
    float Nbias;
    float Rbias;
    float Lbias;

    public SVMDetector(Resources r) {
        InputStream stream = r.openRawResource(R.raw.params);
        String jsonString = new Scanner(stream).useDelimiter("\\A").next();
        try {
            JSONObject params = new JSONObject(jsonString);
            Nbias = (float) params.getDouble("N_bias");
            Rbias = (float) params.getDouble("R_bias");
            Lbias = (float) params.getDouble("L_bias");
            JSONArray Nparam = params.getJSONArray("N");
            JSONArray Rparam = params.getJSONArray("R");
            JSONArray Lparam = params.getJSONArray("L");
            for (int i = 0; i < 1434; ++i) {
                Np[i] = (float) Nparam.getDouble(i);
                Rp[i] = (float) Rparam.getDouble(i);
                Lp[i] = (float) Lparam.getDouble(i);
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void setSensitivity(float s) {
        sensitivity = s;
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

        float[] optMat = new float[16];
        float[] resultMat = new float[16];

        Matrix.setIdentityM(transformMatrix, 0);
        Matrix.setIdentityM(optMat, 0);
        Matrix.translateM(transformMatrix, 0, -bx, -by, -bz);
        Matrix.scaleM(optMat, 0, scalar, scalar, scalar);
        Matrix.multiplyMM(resultMat, 0, optMat, 0, transformMatrix, 0);
        transformMatrix = resultMat;

        // k is the axis of rotation to rotate reference vector to (0, 0, -1)
        // theta is the angle of rotation
        float kx = -ry;
        float ky = rx;
        float kz = 0;
        float theta = (float) (Math.acos(-rz * scalar) * 180 / Math.PI);

        Matrix.setIdentityM(optMat, 0);
        Matrix.rotateM(optMat, 0, theta, kx, ky, kz);
        Matrix.multiplyMM(resultMat, 0, optMat, 0, transformMatrix, 0);
        transformMatrix = resultMat;

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
        theta = (float) (Math.acos(-uy * scalar) * 180 / Math.PI);

        Matrix.setIdentityM(optMat, 0);
        Matrix.rotateM(optMat, 0, theta, kx, ky, kz);
        Matrix.multiplyMM(resultMat, 0, optMat, 0, transformMatrix, 0);
        transformMatrix = resultMat;
    }

    public Enum<MainActivity.Blink> detectBlink(List<NormalizedLandmark> landmarks) {

        float Nscore = -Nbias + (0.9f - sensitivity * 1.2f);
        float Rscore = -Rbias;
        float Lscore = -Lbias;

        for (int i = 0; i < 1434; i = i + 3) {
            float[] lm = transform(landmarks.get(i / 3));
            Nscore += Np[i] * lm[0] + Np[i + 1] * lm[1] + Np[i + 2] * lm[2];
            Rscore += Rp[i] * lm[0] + Rp[i + 1] * lm[1] + Rp[i + 2] * lm[2];
            Lscore += Lp[i] * lm[0] + Lp[i + 1] * lm[1] + Lp[i + 2] * lm[2];
        }

        if (Nscore > Rscore && Nscore > Lscore) {
            return MainActivity.Blink.NONE;
        }
        if (Rscore > Lscore) {
            return MainActivity.Blink.LEFT;
        }
        return MainActivity.Blink.RIGHT;
    }

    public Enum<MainActivity.Shake> detectShake(List<NormalizedLandmark> landmarks) {

        // Base coordinate (Back-center of the face)
        float bx = (landmarks.get(93).getX() + landmarks.get(323).getX()) / 2;
        float bz = (landmarks.get(93).getZ() + landmarks.get(323).getZ()) / 2;

        // Reference vector (Vector from base to nose)
        float rx = landmarks.get(1).getX() - bx;
        float rz = landmarks.get(1).getZ() - bz;

        if (rx / rz > 0.4) return MainActivity.Shake.RIGHT;
        if (rx / rz < -0.4) return MainActivity.Shake.LEFT;
        return MainActivity.Shake.NONE;
    }
}
