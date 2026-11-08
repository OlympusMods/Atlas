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
    public double[] post;

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
        view(px, py, pz, out);
        return screen(out[0], out[1], out[2], width, height, out);
    }

    public void view(double px, double py, double pz, double[] out) {
        double vx = px - x;
        double vy = py - y;
        double vz = pz - z;
        double cx = vx * rx + vz * rz;
        double cy = vx * ux + vy * uy + vz * uz;
        double cz = -(vx * fx + vy * fy + vz * fz);
        double[] m = post;
        if (m != null) {
            double tx = m[0] * cx + m[1] * cy + m[2] * cz + m[3];
            double ty = m[4] * cx + m[5] * cy + m[6] * cz + m[7];
            cz = m[8] * cx + m[9] * cy + m[10] * cz + m[11];
            cx = tx;
            cy = ty;
        }
        out[0] = cx;
        out[1] = cy;
        out[2] = -cz;
    }

    public boolean screen(double cx, double cy, double depth, double width, double height, double[] out) {
        out[2] = depth;
        if (depth <= 0.05) {
            return false;
        }
        out[0] = (0.5 + cx / (depth * tanHalf * aspect) * 0.5) * width;
        out[1] = (0.5 - cy / (depth * tanHalf) * 0.5) * height;
        return true;
    }
}
