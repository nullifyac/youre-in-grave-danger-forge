package com.b1n_ry.yigd.data;

import java.util.Objects;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.Direction;
import net.minecraft.util.math.vector.Vector3i;

public class DirectionalPos {
    private final BlockPos pos;
    private final Direction dir;

    public DirectionalPos(BlockPos pos, Direction dir) {
        this.pos = pos;
        this.dir = dir;
    }

    public DirectionalPos(int x, int y, int z, Direction dir) {
        this(new BlockPos(x, y, z), dir);
    }

    public BlockPos pos() {
        return this.pos;
    }

    public Direction dir() {
        return this.dir;
    }

    public double getSquaredDistance(Vector3i pos) {
        return this.pos.distSqr(pos);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof DirectionalPos)) {
            return false;
        }
        DirectionalPos other = (DirectionalPos) obj;
        return Objects.equals(this.pos, other.pos) && this.dir == other.dir;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.pos, this.dir);
    }
}
