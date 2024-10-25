package tukano.impl.rest;

import java.util.HashSet;
import java.util.Set;

import jakarta.ws.rs.core.Application;
import tukano.impl.Token;
import tukano.impl.rest.utils.CustomLoggingFilter;
import tukano.impl.rest.utils.GenericExceptionMapper;

public class TukanoRestApplication extends Application {

    private Set<Object> singletons = new HashSet<>();
    private Set<Class<?>> resources = new HashSet<>();

    public TukanoRestApplication() {
        resources.add(RestBlobsResource.class);
        resources.add(RestShortsResource.class);
        resources.add(RestUsersResource.class);

        singletons.add(new CustomLoggingFilter());
        singletons.add(new GenericExceptionMapper());

        // Load properties later
        Token.setSecret("SECRET_SCC");
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
