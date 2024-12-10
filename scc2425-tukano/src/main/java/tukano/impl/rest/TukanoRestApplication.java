package tukano.impl.rest;

import java.util.HashSet;
import java.util.Set;

import jakarta.ws.rs.core.Application;
import tukano.impl.Token;
import tukano.impl.auth.Authentication;
import tukano.impl.auth.ControlResource;
import tukano.impl.auth.requestCookies.RequestCookiesFilter;
import tukano.impl.auth.requestCookies.RequestCookiesCleanupFilter;
import tukano.impl.rest.utils.CustomLoggingFilter;
import tukano.impl.rest.utils.GenericExceptionMapper;
import utils.Props;

public class TukanoRestApplication extends Application {

    private Set<Object> singletons = new HashSet<>();
    private Set<Class<?>> resources = new HashSet<>();

    public TukanoRestApplication() {

        Props.load("azurekeys-region.props");

        resources.add(RestBlobsResource.class);
        resources.add(RestShortsResource.class);
        resources.add(RestUsersResource.class);

        singletons.add(new CustomLoggingFilter());
        singletons.add(new GenericExceptionMapper());

        resources.add(ControlResource.class);

        resources.add(RequestCookiesFilter.class);
        resources.add(RequestCookiesCleanupFilter.class);
        resources.add(Authentication.class);

        Token.setSecret("6031360756");
    }

    @Override
    public Set<Class<?>> getClasses() {
        return resources;
    }

    @Override
    public Set<Object> getSingletons() {
        return singletons;
    }
}