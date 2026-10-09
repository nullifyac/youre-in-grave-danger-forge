package com.b1n_ry.yigd.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.EnumMap;

public class TimePoint {
    private final long time;
    private final long timeOfDay;
    private final LocalDateTime irlTime;

    private static final EnumMap<Month, String> MONTH_NAMES;

    public TimePoint(ServerLevel world) {
        this(world.getGameTime(), world.getOverworldClockTime(), LocalDateTime.now());
    }
    public TimePoint(long time, long timeOfDay, LocalDateTime irlTime) {
        this.time = time;
        this.timeOfDay = timeOfDay;
        this.irlTime = irlTime;
    }

    public long getTime() {
        return this.time;
    }
    public long getDay() {
        return this.timeOfDay / 24000;
    }
    public String getMonthName() {
        return MONTH_NAMES.get(this.irlTime.getMonth());
    }
    public int getDate() {
        return this.irlTime.getDayOfMonth();
    }
    public int getYear() {
        return this.irlTime.getYear();
    }
    public int getHour(boolean timePostfix) {
        int hour = this.irlTime.getHour();
        return timePostfix ? (11 + hour) % 12 + 1 : hour;
    }
    public int getMinute() {
        return this.irlTime.getMinute();
    }
    public String getTimePostfix(boolean actuallyUseIt) {
        if (actuallyUseIt)
            return this.irlTime.getHour() >= 12 ? " PM" : " AM";

        return "";
    }

    public CompoundTag toNbt() {
        CompoundTag nbt = new CompoundTag();
        nbt.putLong("time", this.time);
        nbt.putLong("timeOfDay", this.timeOfDay);

        CompoundTag irlTimeNbt = new CompoundTag();
        irlTimeNbt.putInt("year", this.irlTime.getYear());
        irlTimeNbt.putInt("month", this.irlTime.getMonthValue());
        irlTimeNbt.putInt("date", this.irlTime.getDayOfMonth());

        irlTimeNbt.putInt("hour", this.irlTime.getHour());
        irlTimeNbt.putInt("minute", this.irlTime.getMinute());

        nbt.put("realTime", irlTimeNbt);
        return nbt;
    }

    public static TimePoint fromNbt(CompoundTag nbt) {
        long time = nbt.getLongOr("time", 0L);
        long timeOfDay = nbt.getLongOr("timeOfDay", 0L);

        CompoundTag irlTimeNbt = nbt.getCompoundOrEmpty("realTime");
        int year = irlTimeNbt.getIntOr("year", 1970);
        int month = irlTimeNbt.getIntOr("month", 1);
        int date = irlTimeNbt.getIntOr("date", 1);
        int hour = irlTimeNbt.getIntOr("hour", 0);
        int minute = irlTimeNbt.getIntOr("minute", 0);

        LocalDateTime dateTime = LocalDateTime.of(year, month, date, hour, minute);
        return new TimePoint(time, timeOfDay, dateTime);
    }

    static {
        MONTH_NAMES = new EnumMap<>(Month.class) {{
            put(Month.JANUARY, "January");
            put(Month.FEBRUARY, "February");
            put(Month.MARCH, "March");
            put(Month.APRIL, "April");
            put(Month.MAY, "May");
            put(Month.JUNE, "June");
            put(Month.JULY, "July");
            put(Month.AUGUST, "August");
            put(Month.SEPTEMBER, "September");
            put(Month.OCTOBER, "October");
            put(Month.NOVEMBER, "November");
            put(Month.DECEMBER, "December");
        }};
    }
}
