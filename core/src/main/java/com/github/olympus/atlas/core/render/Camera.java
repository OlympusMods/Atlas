package com.github.olympus.atlas.core.render;

public final class Camera {
    public final double x;
    public final double y;
    public final double z;
    public final double fx;
    public final double fy;
    public final double fz;
    public final double rx;
    public final double rz;
    public final double ux;
    public final double uy;
    public final double uz;
    public final double tanHalf;
    public final double aspect;

    public Camera(double x, double y, double z, double yawDegrees, double pitchDegrees, double fovDegrees,
            double aspect) {
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        this.x = x;
        this.y = y;
        this.z = z;
        this.fx = -Math.sin(yaw) * Math.cos(pitch);
        this.fy = -Math.sin(pitch);
        this.fz = Math.cos(yaw) * Math.cos(pitch);
        this.rx = -Math.cos(yaw);
        this.rz = -Math.sin(yaw);
        this.ux = -rz * fy;
        this.uy = rz * fx - rx * fz;
        this.uz = rx * fy;
        this.tanHalf = Math.tan(Math.toRadians(fovDegrees) / 2.0);
        this.aspect = aspect;
    }

    public static Camera orbit(double targetX, double targetY, double targetZ, double yaw, double pitch,
            double distance, double fov, double aspect) {
        Camera facing = new Camera(0, 0, 0, yaw, pitch, fov, aspect);
        return new Camera(targetX - facing.fx * distance, targetY - facing.fy * distance,
                targetZ - facing.fz * distance, yaw, pitch, fov, aspect);
    }

    public boolean project(double px, double py, double pz, double width, double height, double[] out) {
        double vx = px - x;
        double vy = py - y;
        double vz = pz - z;
        double depth = vx * fx + vy * fy + vz * fz;
        out[2] = depth;
        if (depth <= 0.05) {
            return false;
        }
        double sx = (vx * rx + vz * rz) / (depth * tanHalf * aspect);
        double sy = (vx * ux + vy * uy + vz * uz) / (depth * tanHalf);
        out[0] = (0.5 + sx * 0.5) * width;
        out[1] = (0.5 - sy * 0.5) * height;
        return true;
    }
}
