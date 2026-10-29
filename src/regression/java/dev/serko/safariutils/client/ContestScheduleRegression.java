package dev.serko.safariutils.client;
public final class ContestScheduleRegression {
    public static int run() {
        int checks=0;
        for(int year=1;year<=1000;year++) {
            long start=SkyblockCalendar.dateMillis(year,0,1)+4000;
            require(ContestSchedule.remaining(start)==-1,"year start unavailable");
            require(ContestSchedule.remaining(start+ContestSchedule.NEW_YEAR_DELAY-1)==-1,"just before scoring");
            require(ContestSchedule.remaining(start+ContestSchedule.NEW_YEAR_DELAY)==520000,"8:40 scoring window");
            require(ContestSchedule.untilNext(start)==ContestSchedule.NEW_YEAR_DELAY,"wait to delayed start");
            require(ContestSchedule.untilNext(start-1)==ContestSchedule.NEW_YEAR_DELAY+1,"preceding gap includes delay");
            require(ContestSchedule.remaining(start+ContestSchedule.ACTIVE)==-1,"original end retained");
            require(ContestSchedule.remaining(start+ContestSchedule.CYCLE)==ContestSchedule.ACTIVE,"next day normal");
            require(ContestSchedule.remaining(start-ContestSchedule.CYCLE)==ContestSchedule.ACTIVE,"previous day normal");
            require(ContestSchedule.cycle(start)==ContestSchedule.cycle(start+ContestSchedule.NEW_YEAR_DELAY),"same persisted cycle");
            checks+=9;
        }
        return checks;
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
