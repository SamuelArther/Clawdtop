package clawdtop;

import java.util.ArrayDeque;

/**
 * Where Clawd's body is on the screen and how it moves: sitting on his perch, hopping onto your cursor and riding it,
 * flying off when you shake it, landing head first, lying there dizzy, getting up and walking home. Positions are
 * screen pixels for the point between his feet. No windows here, so it can be tested on its own.
 */
public final class Body {
    public enum State { HOME, HOP_ON, RIDE, FALL, DIZZY, SHAKE, WALK, HOP_TO, PERCH, AWAY, OUT, FLY, ROCKET, LAP }

    static final double GRAVITY = 2600;      // px/s², a quick, cartoony fall
    static final double WALK_SPEED = 140;    // px/s
    static final long HOVER_TO_HOP = 450;    // ms of the cursor waiting next to him before he hops on
    static final long HOP_TIME = 260;
    static final long DIZZY_TIME = 1100;
    static final long FLIP_TIME = 260;       // getting back onto his feet
    static final long SHAKE_TIME = 800;      // shaking it off once he's up
    static final int SHAKE_TURNS = 4;        // direction changes within half a second that count as shaking
    static final double SHAKE_SPEED = 1400;  // and how fast (px/s on average) the cursor has to be going
    static final long FLY_TIME = 12_000;     // a ride on his flying carpet
    static final long ROCKET_TIME = 9000;    // his rocket's flight, before it crashes
    static final double LIFTOFF_SPEED = 1500, CRUISE_SPEED = 260; // px/s: a blast off, then a lot slower

    private double ceiling = Double.NaN;
    static final double LAP_SPEED = 900; // px/s: as fast as he can
    private double unitPx = 4;            // how big his pixels are on the screen (for running up walls)

    /** How many screen pixels one of his pixels is. */
    public void setUnit(double unit) {
        unitPx = unit;
    }

    /** Runs a lap: along the floor, up the wall, across the ceiling (upside down), down the other wall, and home. */
    public void runLap() {
        if (state == State.HOME) set(State.LAP);
    }

    /** The top of the screen (his rocket bounces off it). */
    public void setCeiling(double top) {
        ceiling = top;
    }

    private boolean rides = true, shakeOff = true;
    private long hoverToHop = HOVER_TO_HOP;
    private double shakeSpeed = SHAKE_SPEED, walkSpeed = WALK_SPEED;

    /** His riding and walking options: whether he rides and can be shaken off, how long before he hops on (ms), how
     * fast you have to shake, and how fast he walks. */
    public void setRules(boolean rides, boolean shakeOff, long hoverToHop, double shakeSpeed, double walkSpeed) {
        this.rides = rides;
        this.shakeOff = shakeOff;
        this.hoverToHop = hoverToHop;
        this.shakeSpeed = shakeSpeed;
        this.walkSpeed = walkSpeed;
    }

    private State state = State.HOME;
    private long stateFor;
    private double x, y;          // between his feet
    private double vx, vy;
    private double angle;         // 0 standing, PI upside down
    private double hopFromX, hopFromY;
    private long hover;
    private final ArrayDeque<double[]> trail = new ArrayDeque<>(); // recent cursor spots: {time ms, x, y}
    private long time;
    private long stillFor;        // ms the cursor hasn't moved while he rides
    private boolean onJob;        // riding to a job: he stays on until he's told where to go
    private boolean headFirst = true; // a fall from being shaken off; hopping down from a window he lands on his feet
    private double targetX, targetY;  // a spot he's going to, or sitting on (like the top edge of a window)

    /**
     * Moves time on by ms. cursor is where the mouse is; homeX/groundY is his perch (the taskbar's top edge);
     * reach is how far beside him (px) the cursor may wait for him to hop on; left/right are the screen's edges.
     */
    public void tick(long ms, double cursorX, double cursorY, double homeX, double groundY, double reach, double left, double right) {
        time += ms;
        stateFor += ms;
        double dt = ms / 1000.0;
        trail.addLast(new double[] {time, cursorX, cursorY});
        while (!trail.isEmpty() && trail.peekFirst()[0] < time - 500) trail.removeFirst();

        switch (state) {
            case HOME -> {
                x = homeX;
                y = groundY;
                angle = 0;
                // The cursor waits on the taskbar's top edge right beside him (not on him): he hops on
                double side = Math.abs(cursorX - x);
                boolean beside = Math.abs(cursorY - groundY) <= 10 && side > reach * 0.35 && side < reach;
                hover = beside && rides ? hover + ms : 0;
                if (hover > hoverToHop) {
                    hopFromX = x;
                    hopFromY = y;
                    set(State.HOP_ON);
                }
            }
            case HOP_TO, PERCH -> {
                double t = state == State.PERCH ? 1 : Math.min(1, stateFor / (double) HOP_TIME);
                x = hopFromX + (targetX - hopFromX) * t;
                y = hopFromY + (targetY - hopFromY) * t - Math.sin(Math.PI * t) * 50;
                angle = 0;
                if (state == State.HOP_TO && t >= 1) set(State.PERCH);
            }
            case HOP_ON -> {
                double t = Math.min(1, stateFor / (double) HOP_TIME);
                x = hopFromX + (cursorX - hopFromX) * t;
                y = hopFromY + (cursorY - hopFromY) * t - Math.sin(Math.PI * t) * 40; // a little arc
                if (t >= 1) {
                    stillFor = 0;
                    set(State.RIDE);
                }
            }
            case RIDE -> {
                double[] speed = cursorSpeed();
                x = cursorX;
                y = cursorY;
                stillFor = Math.hypot(speed[0], speed[1]) < 30 ? stillFor + ms : 0;
                if (shakeOff && shaking()) {
                    onJob = false;
                    headFirst = true;
                    vx = Math.max(-900, Math.min(900, speed[0] * 0.6));
                    vy = Math.min(-250, speed[1] * 0.3 - 250); // flung up and out
                    set(State.FALL);
                } else if (!onJob && Math.abs(cursorY - groundY) <= 6 && stillFor > 700 && stateFor > 1000) {
                    // You brought him back down to the taskbar and stopped: he hops off and walks home
                    y = groundY;
                    set(State.WALK);
                }
            }
            case FALL -> {
                vy += GRAVITY * dt;
                vx *= Math.pow(0.6, dt);
                x = Math.max(left, Math.min(right, x + vx * dt));
                y += vy * dt;
                angle = headFirst ? Math.min(Math.PI, stateFor / 160.0 * Math.PI) : 0; // shaken off: he flips, head first
                if (y >= groundY) {
                    y = groundY;
                    if (headFirst) {
                        angle = Math.PI;
                        set(State.DIZZY);
                    } else {
                        set(State.WALK);
                    }
                }
            }
            case DIZZY -> {
                y = groundY;
                if (stateFor > DIZZY_TIME) {
                    // back onto his feet: the rest of the way round
                    angle = Math.PI + Math.min(Math.PI, (stateFor - DIZZY_TIME) / (double) FLIP_TIME * Math.PI);
                    if (stateFor > DIZZY_TIME + FLIP_TIME) {
                        angle = 0;
                        set(State.SHAKE);
                    }
                }
            }
            case AWAY -> {
                y = groundY;
                angle = 0;
                double step = walkSpeed * 1.6 * dt; // stomping off
                if (Math.abs(targetX - x) <= step) {
                    x = targetX;
                    set(State.OUT);
                } else {
                    x += Math.signum(targetX - x) * step;
                }
            }
            case OUT -> {
                if (stateFor > awayFor) set(State.WALK); // feeling better: back home
            }
            case FLY -> {
                // Up off the taskbar, swooping round the screen in big loops, and back home
                double u = stateFor / (double) FLY_TIME;
                if (u >= 1) {
                    x = homeX;
                    y = groundY;
                    set(State.HOME);
                    break;
                }
                double in = Math.min(1, u / 0.12), out = Math.min(1, (1 - u) / 0.12);
                double w = in * in * (3 - 2 * in) * out * out * (3 - 2 * out);
                double loopX = (left + right) / 2 + (right - left) * 0.36 * Math.sin(Math.PI * 2 * 1.5 * u);
                double loopY = groundY - 280 + 110 * Math.sin(Math.PI * 2 * 3 * u);
                x = homeX + (loopX - homeX) * w;
                y = groundY + (loopY - groundY) * w;
                angle = 0;
            }
            case ROCKET -> {
                // Blasts off at full speed, then cruises round the screen (a lot slower), wandering, bouncing off
                // the edges, sputtering at the end... then BOOM
                double ceiling = Double.isNaN(this.ceiling) ? groundY - 700 : this.ceiling;
                double speed = Math.hypot(vx, vy);
                double want = stateFor < 600 ? LIFTOFF_SPEED : CRUISE_SPEED;
                speed += (want - speed) * Math.min(1, dt * (stateFor < 600 ? 10 : 2));
                double dir = Math.atan2(vy, vx);
                if (stateFor > 800) dir += (Math.sin(time / 650.0) * 1.4 + Math.sin(time / 230.0) * 0.7) * dt; // wandering
                vx = Math.cos(dir) * speed;
                vy = Math.sin(dir) * speed;
                x += vx * dt;
                y += vy * dt;
                if (x < left + 40) {
                    x = left + 40;
                    vx = Math.abs(vx);
                } else if (x > right - 40) {
                    x = right - 40;
                    vx = -Math.abs(vx);
                }
                if (y < ceiling + 110) { // (his rocket's tall: y is its bottom)
                    y = ceiling + 110;
                    vy = Math.abs(vy);
                } else if (stateFor > 1000 && y > groundY - 20) {
                    y = groundY - 20;
                    vy = -Math.abs(vy);
                }
                if (stateFor > ROCKET_TIME - 1500) { // sputtering
                    x += Math.sin(time / 30.0) * 3;
                    y += Math.cos(time / 37.0) * 2;
                }
                // it points the way it's going (0 is straight up)
                double heading = Math.atan2(vx, -vy);
                angle += Math.IEEEremainder(heading - angle, Math.PI * 2) * Math.min(1, dt * 8);
                if (stateFor >= ROCKET_TIME) {
                    boom = true;
                    angle = 0;
                    headFirst = true;
                    vx = (x > (left + right) / 2 ? -1 : 1) * 900; // flung back across the screen
                    vy = -1200;
                    set(State.FALL);
                }
            }
            case LAP -> {
                // The path his feet take, round the edge of the screen; he turns at each corner so his feet stay on it
                double top = Double.isNaN(ceiling) ? groundY - 700 : ceiling;
                double[] legs = {homeX - left, groundY - top, right - left, groundY - top, right - homeX};
                double[] turns = {0, Math.PI / 2, Math.PI, Math.PI * 1.5, Math.PI * 2};
                double d = stateFor / 1000.0 * LAP_SPEED;
                int leg = 0;
                while (leg < legs.length && d > legs[leg]) {
                    d -= legs[leg];
                    leg++;
                }
                if (leg >= legs.length) {
                    x = homeX;
                    y = groundY;
                    angle = 0;
                    set(State.HOME);
                    break;
                }
                double fx, fy;
                switch (leg) {
                    case 0 -> { fx = homeX - d; fy = groundY; }
                    case 1 -> { fx = left; fy = groundY - d; }
                    case 2 -> { fx = left + d; fy = top; }
                    case 3 -> { fx = right; fy = top + d; }
                    default -> { fx = right - legs[4] + d; fy = groundY; }
                }
                double from = leg == 0 ? 0 : turns[leg - 1];
                double turn = from + (turns[leg] - from) * Math.min(1, d / (8 * unitPx)); // round the corner
                angle = turn;
                // where his window goes so his feet are at (fx, fy) while he's turned (he turns round his middle)
                double r = Sprite.spinRadius() * unitPx;
                x = fx + r * Math.sin(turn);
                y = fy + r - r * Math.cos(turn);
            }
            case SHAKE -> {
                y = groundY;
                angle = 0;
                if (stateFor > SHAKE_TIME) set(State.WALK);
            }
            case WALK -> {
                y = groundY;
                angle = 0;
                double step = walkSpeed * dt;
                if (Math.abs(homeX - x) <= step) {
                    x = homeX;
                    set(State.HOME);
                } else {
                    x += Math.signum(homeX - x) * step;
                }
            }
        }
    }

    /**
     * Shoots him straight up out of his box at full speed (a little to one side), so he comes down on his head, gets up,
     * shakes it off and walks back.
     */
    public void launch(double sideways) {
        onJob = false;
        headFirst = true;
        vx = sideways;
        vy = -1700;
        set(State.FALL);
    }

    /** Off on a ride on his flying carpet (from home). */
    public void flyCarpet() {
        if (state == State.HOME) set(State.FLY);
    }

    private boolean boom;

    /** Off on his rocket (from home). */
    public void rocketRide() {
        if (state != State.HOME) return;
        angle = 0;
        vx = 0;
        vy = -LIFTOFF_SPEED; // straight up, full speed
        set(State.ROCKET);
    }

    /** Whether his rocket just blew up (once). */
    public boolean takeBoom() {
        boolean b = boom;
        boom = false;
        return b;
    }

    /** His carpet's gone out from under him: he falls, head first. */
    public void knockOff() {
        if (state != State.FLY) return;
        headFirst = true;
        vx = 0;
        vy = -150;
        set(State.FALL);
    }

    /** Shoots him out of his box at (fromX, fromY): see launch. (His body hasn't been anywhere yet, the first time.) */
    public void launchFrom(double fromX, double fromY, double sideways) {
        x = fromX;
        y = fromY;
        angle = 0;
        launch(sideways);
    }

    private long awayFor;

    /** Stomps off to x (off the edge of the screen), stays away for ms, then walks back home. */
    public void walkOff(double x, long ms) {
        if (state != State.HOME) return;
        targetX = x;
        awayFor = ms;
        set(State.AWAY);
    }

    /** Walks in from x (off the side of the screen) to home: arriving at a new computer. */
    public void walkIn(double fromX) {
        x = fromX;
        angle = 0;
        set(State.WALK);
    }

    /** Drops in from above (the top of the screen) at x, landing on his feet, then walks home. */
    public void dropIn(double atX, double fromY) {
        onJob = false;
        headFirst = false;
        x = atX;
        y = fromY;
        vx = 0;
        vy = 0;
        set(State.FALL);
    }

    /** Hops onto the cursor for a job, and stays on (no hopping off at the taskbar) until told where to go. */
    public void board() {
        if (state != State.HOME && state != State.WALK) return;
        onJob = true;
        hopFromX = x;
        hopFromY = y;
        set(State.HOP_ON);
    }

    /** Whether he's riding the cursor for a job (not shaken off). */
    public boolean riding() {
        return onJob && (state == State.HOP_ON || state == State.RIDE);
    }

    /** Hops (from wherever he is) to a spot, like the top edge of a window, and sits there. Call again as it moves. */
    public void perchAt(double spotX, double spotY) {
        if (state != State.HOP_TO && state != State.PERCH) {
            hopFromX = x;
            hopFromY = y;
            onJob = false;
            set(State.HOP_TO);
        }
        targetX = spotX;
        targetY = spotY;
        if (state == State.PERCH) {
            hopFromX = spotX;
            hopFromY = spotY;
        }
    }

    /** Done with a job: hops down from wherever he's sitting (landing on his feet) and walks home. */
    public void leave() {
        onJob = false;
        if (state == State.HOME) return;
        headFirst = false;
        vx = 0;
        vy = -350;
        set(State.FALL);
    }

    /** The cursor's average speed over the last half second, {x, y} in px/s. */
    private double[] cursorSpeed() {
        if (trail.size() < 2) return new double[] {0, 0};
        double[] first = trail.peekFirst();
        double[] last = trail.peekLast();
        double seconds = Math.max(0.001, (last[0] - first[0]) / 1000.0);
        return new double[] {(last[1] - first[1]) / seconds, (last[2] - first[2]) / seconds};
    }

    /** Whether the cursor's being shaken: back and forth a few times, fast, within the last half second. */
    boolean shaking() {
        if (trail.size() < 4) return false;
        int turns = 0;
        double distance = 0;
        double lastDx = 0, lastDy = 0;
        double[] previous = null;
        for (double[] p : trail) {
            if (previous != null) {
                double dx = p[1] - previous[1];
                double dy = p[2] - previous[2];
                distance += Math.hypot(dx, dy);
                if (Math.abs(dx) > 2 && lastDx != 0 && Math.signum(dx) != Math.signum(lastDx)) turns++;
                if (Math.abs(dy) > 2 && lastDy != 0 && Math.signum(dy) != Math.signum(lastDy)) turns++;
                if (Math.abs(dx) > 2) lastDx = dx;
                if (Math.abs(dy) > 2) lastDy = dy;
            }
            previous = p;
        }
        double seconds = Math.max(0.001, (trail.peekLast()[0] - trail.peekFirst()[0]) / 1000.0);
        return turns >= SHAKE_TURNS && distance / seconds > shakeSpeed;
    }

    private void set(State next) {
        state = next;
        stateFor = 0;
        hover = 0;
    }

    public State state() {
        return state;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    /** How far he's turned: 0 standing, PI upside down. */
    public double angle() {
        return angle;
    }

    /** ms in this state, for animations. */
    public long stateFor() {
        return stateFor;
    }
}
