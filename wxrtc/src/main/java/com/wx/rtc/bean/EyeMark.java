package com.wx.rtc.bean;

public class EyeMark {

    /**
     * normal : 0
     * femtosecond : 0
     * astigmatism : 0
     */

    //普通：0为无，1为有
    private int normal;
    //飞秒：0为无，1为有
    private int femtosecond;
    //散光：0为无，1为有
    private int astigmatism;

    public int getNormal() {
        return normal;
    }

    public void setNormal(int normal) {
        this.normal = normal;
    }

    public int getFemtosecond() {
        return femtosecond;
    }

    public void setFemtosecond(int femtosecond) {
        this.femtosecond = femtosecond;
    }

    public int getAstigmatism() {
        return astigmatism;
    }

    public void setAstigmatism(int astigmatism) {
        this.astigmatism = astigmatism;
    }
}
