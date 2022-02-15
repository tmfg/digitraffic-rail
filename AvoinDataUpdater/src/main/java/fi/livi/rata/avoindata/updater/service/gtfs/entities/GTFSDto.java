package fi.livi.rata.avoindata.updater.service.gtfs.entities;

import java.util.ArrayList;
import java.util.List;

public class GTFSDto {
    public List<Agency> agencies = new ArrayList<>();
    public List<Route> routes = new ArrayList<>();
    public List<Trip> trips = new ArrayList<>();
    public List<Stop> stops = new ArrayList<>();
    public List<Shape> shapes = new ArrayList<>();
}
