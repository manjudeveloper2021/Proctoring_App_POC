package com.example.proctoring_app;

import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.util.ArrayList;
import java.util.List;

public class LipDetector {
    private CascadeClassifier lipCascade;
    private static final int MIN_CONTOUR_AREA = 1000; // Adjust this threshold as needed

    public boolean isLipMovementDetected(Mat frame) {
        Mat grayFrame = new Mat();
        Imgproc.cvtColor(frame, grayFrame, Imgproc.COLOR_RGBA2GRAY);

        // Apply Gaussian blur to reduce noise
        Imgproc.GaussianBlur(grayFrame, grayFrame, new Size(5, 5), 0);

        // Use adaptive thresholding to binarize the frame
        Imgproc.adaptiveThreshold(grayFrame, grayFrame, 255, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, 11, 2);

        // Find contours in the binary image
        List<MatOfPoint> contours = new ArrayList<>();
        Imgproc.findContours(grayFrame, contours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        // Check if any contour has an area greater than the threshold
        for (MatOfPoint contour : contours) {
            double area = Imgproc.contourArea(contour);
            if (area > MIN_CONTOUR_AREA) {
                return true;
            }
        }

        return false;
    }
    public Rect[] detectLips(Mat inputFrame) {
        Mat grayFrame = new Mat();
        Imgproc.cvtColor(inputFrame, grayFrame, Imgproc.COLOR_RGBA2GRAY);
        Imgproc.equalizeHist(grayFrame, grayFrame);

        MatOfRect lips = new MatOfRect();
        lipCascade.detectMultiScale(grayFrame, lips, 1.1, 3, 0, new Size(20, 20), new Size());

        return lips.toArray();
    }
}
