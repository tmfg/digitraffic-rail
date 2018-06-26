package fi.livi.rata.avoindata.server.config;


import fi.livi.rata.avoindata.server.controller.utils.CacheControl;

public class CacheConfig {
    public static CacheControl LIVE_TRAIN_ALL_TRAINS_CACHECONTROL = new CacheControl(10, 120, 120, 5 * 60);
    public static CacheControl LIVE_TRAIN_STATION_CACHECONTROL = new CacheControl(5, 15, 30, 30);
    public static CacheControl LIVE_TRAIN_SINGLE_TRAIN_CACHECONTROL = new CacheControl(5, 10, 10, 10);

    public static CacheControl SCHEDULE_STATION_CACHECONTROL = new CacheControl(0, 0, 180, 180);

    public static CacheControl TRAIN_RUNNING_MESSAGE_CACHECONTROL = new CacheControl(3, 60, 15, 15);

    public static CacheControl COMPOSITION_CACHECONTROL = new CacheControl(10, 120, 180, 180);

    public static CacheControl METADATA_CACHECONTROL = new CacheControl(0, 0, 600, 600);

}
