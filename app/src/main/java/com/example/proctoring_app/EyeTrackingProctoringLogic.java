package com.example.proctoring_app;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

public class EyeTrackingProctoringLogic {
    private CascadeClassifier eyeCascade;
    private Rect[] prevEyes; // Store previous eye positions
    public EyeTrackingProctoringLogic(String eyeCascadeFilePath) {
        eyeCascade = new CascadeClassifier(eyeCascadeFilePath);
    }

    public void processFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        Mat frame = inputFrame.rgba();

        Mat grayFrame = new Mat();
        Imgproc.cvtColor(frame, grayFrame, Imgproc.COLOR_RGBA2GRAY);

        // Detect eyes
        MatOfRect eyes = new MatOfRect();
        eyeCascade.detectMultiScale(grayFrame, eyes, 1.1, 2, 0, new Size(30, 30), new Size());

        Rect[] detectedEyes = eyes.toArray();

        // Check if eyes have moved
        if (prevEyes != null) {
            for (Rect prevEye : prevEyes) {
                for (Rect eye : detectedEyes) {
                    if (eye.contains(prevEye.tl()) && eye.contains(prevEye.br())) {
                        // Eyes haven't moved significantly
                        // Implement your proctoring logic here
                        // ...
                        break;
                    }
                }
            }
        }

        prevEyes = detectedEyes;
    }
}
