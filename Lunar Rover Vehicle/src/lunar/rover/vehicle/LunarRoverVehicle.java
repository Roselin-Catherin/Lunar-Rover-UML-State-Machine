package lunar.rover.vehicle;

/*
 * LunarRoverVehicle.java
 * Implementation of the Apollo 15 LRV state machine.
 *
 * Pedal input rules (same as the diagram legend):
 *   tap         = pedal pressed and released in under 1 s
 *   hold(t)     = pedal held for t seconds, checked when it is released
 *   double tap  = two taps within 0.5 s
 *
 * Any event that a state does not handle is simply ignored.
 *
 * Compile: javac lunar/rover/vehicle/LunarRoverVehicle.java
 * Run:     java lunar.rover.vehicle.LunarRoverVehicle
 */
public class LunarRoverVehicle {

    enum State {
        // Driving
        AT_REST, ACCELERATING, DECELERATING, CRUISING, REVERSING,
        // CameraAndDrill
        IDLE,
        COLOR_READY, COLOR_SELF_TIMER,
        CAM16_READY, CAM16_SELF_TIMER,
        DRILL_OFF, DRILL_ON
    }

    static final double TAP_LIMIT = 1.0;      // seconds
    static final double MAX_FORWARD = 12.0;   // km/h, from the rover spec
    static final double MAX_REVERSE = 4.0;    // km/h
    static final double ACCEL = 1.0;          // km/h gained or lost per second
    static final double TIMER_DELAY = 10.0;   // self timer wait in seconds

    private State state;
    private double speed;       // negative when reversing
    private double timerCount;  // seconds spent in SelfTimer

    public LunarRoverVehicle() {
        System.out.println("Rover powered on");
        enter(State.AT_REST);   // Driving -> AtRest (initial states)
    }

    // ---------- helpers for the nested states ----------

    private boolean inDriving() {
        return state.ordinal() <= State.REVERSING.ordinal();
    }

    private boolean inForward() {
        return state == State.ACCELERATING
            || state == State.DECELERATING
            || state == State.CRUISING;
    }

    private boolean inTelevisionControlUnit() {
        return !inDriving() && state != State.IDLE;
    }

    private boolean isTap(double seconds) {
        return seconds < TAP_LIMIT;
    }

    // ---------- state changes with entry / exit actions ----------

    private void changeTo(State next) {
        exit(state);
        enter(next);
    }

    private void exit(State s) {
        if (s == State.DRILL_ON) {
            stopDrill();
        }
    }

    private void enter(State s) {
        System.out.println("  -> " + s);
        state = s;
        switch (s) {
            case AT_REST:
                speed = 0;
                break;
            case DRILL_ON:
                startDrill();
                break;
            case COLOR_SELF_TIMER:
            case CAM16_SELF_TIMER:
                timerCount = 0;
                break;
            default:
                break;
        }
    }

    // ---------- events ----------

    public void leftPedal(double seconds) {
        System.out.println("left pedal " + seconds + "s");
        boolean tap = isTap(seconds);

        switch (state) {
            case AT_REST:
                if (tap) changeTo(State.ACCELERATING);         // into Forward
                else if (seconds > 3) changeTo(State.REVERSING);
                break;
            case DECELERATING:
            case CRUISING:
                if (tap) changeTo(State.ACCELERATING);
                break;
            case IDLE:
                if (seconds >= 5 && seconds < 10) changeTo(State.COLOR_READY);
                else if (seconds >= 10) changeTo(State.CAM16_READY);
                break;
            case COLOR_READY:
                if (tap) takePicture();                         // internal transition
                else if (seconds >= 5) changeTo(State.COLOR_SELF_TIMER);
                break;
            case CAM16_READY:
                if (tap) takePicture();
                else if (seconds >= 5) changeTo(State.CAM16_SELF_TIMER);
                break;
            case DRILL_OFF:
                if (tap) changeTo(State.DRILL_ON);
                break;
            case DRILL_ON:
                if (tap) changeTo(State.DRILL_OFF);
                break;
            default:
                break;  // ignored
        }
    }

    public void rightPedal(double seconds) {
        System.out.println("right pedal " + seconds + "s");
        boolean tap = isTap(seconds);

        // rightTap on TelevisionControlUnit leaves whatever device is active
        if (inTelevisionControlUnit()) {
            if (tap) changeTo(State.IDLE);
            return;
        }

        switch (state) {
            case ACCELERATING:
                if (tap) changeTo(State.DECELERATING);
                else if (seconds > 3) changeTo(State.CRUISING);
                break;
            case CRUISING:
                if (tap) changeTo(State.DECELERATING);
                break;
            case REVERSING:
                if (tap) changeTo(State.AT_REST);
                break;
            default:
                break;
        }
    }

    public void leftDoubleTap() {
        System.out.println("left double tap");
        if (state == State.IDLE) {
            changeTo(State.DRILL_OFF);
        }
    }

    public void flipSwitch() {
        System.out.println("flip switch");
        if (inDriving()) {
            if (speed == 0) {                     // guard [speed = 0]
                changeTo(State.IDLE);             // CameraAndDrill -> Idle
            } else {
                System.out.println("  rover still moving, switch ignored");
            }
        } else {
            changeTo(State.AT_REST);              // back to Driving -> AtRest
        }
    }

    /** Lets time pass so the do-activities and the self timer can run. */
    public void tick(double seconds) {
        switch (state) {
            case ACCELERATING:
                speed = Math.min(MAX_FORWARD, speed + ACCEL * seconds);
                break;
            case DECELERATING:
                speed = Math.max(0, speed - ACCEL * seconds);
                break;
            case CRUISING:
                break;  // hold speed
            case REVERSING:
                speed = Math.max(-MAX_REVERSE, speed - ACCEL * seconds);
                break;
            case COLOR_SELF_TIMER:
            case CAM16_SELF_TIMER:
                timerCount += seconds;
                if (timerCount >= TIMER_DELAY) {   // after(10 s) / takePicture()
                    takePicture();
                    changeTo(state == State.COLOR_SELF_TIMER
                             ? State.COLOR_READY : State.CAM16_READY);
                }
                break;
            default:
                break;
        }

        // when (speed = 0) on Forward
        if (inForward() && speed == 0) {
            changeTo(State.AT_REST);
        }
    }

    // ---------- actions ----------

    private void takePicture() {
        System.out.println("  * picture taken");
    }

    private void startDrill() {
        System.out.println("  * drill started");
    }

    private void stopDrill() {
        System.out.println("  * drill stopped");
    }

    public State getState() { return state; }
    public double getSpeed() { return speed; }

    // ---------- quick test run ----------

    public static void main(String[] args) {
        LunarRoverVehicle rover = new LunarRoverVehicle();

        System.out.println("\n-- driving forward --");
        rover.leftPedal(0.3);
        rover.tick(5);
        System.out.println("  speed = " + rover.getSpeed());
        rover.rightPedal(4);              // cruise
        rover.tick(3);
        System.out.println("  speed = " + rover.getSpeed());
        rover.flipSwitch();               // should be refused, still moving
        rover.rightPedal(0.4);            // slow down
        rover.tick(10);                   // reaches 0, back to AtRest

        System.out.println("\n-- reversing --");
        rover.leftPedal(3.5);
        rover.tick(2);
        System.out.println("  speed = " + rover.getSpeed());
        rover.rightPedal(0.2);

        System.out.println("\n-- cameras --");
        rover.flipSwitch();
        rover.leftPedal(6);               // color camera
        rover.leftPedal(0.5);             // picture
        rover.leftPedal(5);               // self timer
        rover.tick(10);
        rover.rightPedal(0.3);            // back to idle
        rover.leftPedal(11);              // 16mm camera
        rover.leftPedal(0.5);
        rover.rightPedal(0.3);

        System.out.println("\n-- drill --");
        rover.leftDoubleTap();
        rover.leftPedal(0.4);             // drill on
        rover.flipSwitch();               // exit stops the drill
        System.out.println("\nfinal state: " + rover.getState());
    }
}