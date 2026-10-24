package com.fredoseep.chaoxinghook;

import java.util.List;

/** 签到坐标采样模型、有效性检查和三点定位计算。 */
final class SignLocationMath {
    static class LocationPoint {
        double lat;
        double lon;
        double distance;
        LocationPoint(double lat, double lon, double distance) {
            this.lat = lat; this.lon = lon; this.distance = distance;
        }
    }

    static double[] calculateTriangulation(List<LocationPoint> points) {
        if (points.size() < 3) return null;

        LocationPoint p1 = points.get(0);
        LocationPoint p2 = points.get(1);
        LocationPoint p3 = points.get(2);

        double R = 6378137.0;
        double lat0 = p1.lat * Math.PI / 180.0;

        double x1 = 0, y1 = 0;
        double r1 = p1.distance;

        double x2 = (p2.lon - p1.lon) * (Math.PI / 180.0) * R * Math.cos(lat0);
        double y2 = (p2.lat - p1.lat) * (Math.PI / 180.0) * R;
        double r2 = p2.distance;

        double x3 = (p3.lon - p1.lon) * (Math.PI / 180.0) * R * Math.cos(lat0);
        double y3 = (p3.lat - p1.lat) * (Math.PI / 180.0) * R;
        double r3 = p3.distance;

        double A = 2 * x2;
        double B = 2 * y2;
        double C = r1 * r1 - r2 * r2 + x2 * x2 + y2 * y2;

        double D = 2 * x3;
        double E = 2 * y3;
        double F = r1 * r1 - r3 * r3 + x3 * x3 + y3 * y3;

        double det = A * E - B * D;
        if (Math.abs(det) < 1.0) return null;

        double targetX = (C * E - B * F) / det;
        double targetY = (A * F - C * D) / det;

        double targetLon = p1.lon + (targetX / (R * Math.cos(lat0) * (Math.PI / 180.0)));
        double targetLat = p1.lat + (targetY / (R * (Math.PI / 180.0)));

        return new double[]{targetLat, targetLon};
    }

    static boolean validCoordinates(double lat, double lon) {
        return Double.isFinite(lat) && Double.isFinite(lon) && lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180;
    }
}
