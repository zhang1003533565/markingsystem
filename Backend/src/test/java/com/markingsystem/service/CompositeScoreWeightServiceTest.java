package com.markingsystem.service;

import com.markingsystem.entity.CompositeScoreWeight;
import com.markingsystem.repository.CompositeScoreWeightRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompositeScoreWeightServiceTest {

    @Test
    void savesOnlyGlobalWeightConfig() throws Exception {
        CompositeScoreWeight existing = new CompositeScoreWeight();
        CompositeScoreWeight input = new CompositeScoreWeight();
        input.setPhysicalBaseWeight(0.25D);

        CompositeScoreWeightService service = new CompositeScoreWeightService();
        setRepository(service, repositoryWith(existing));

        CompositeScoreWeight saved = service.save(input);

        assertEquals(0.25D, saved.getPhysicalBaseWeight());
    }

    private CompositeScoreWeightRepository repositoryWith(CompositeScoreWeight weight) {
        List<CompositeScoreWeight> store = new ArrayList<>(List.of(weight));
        return (CompositeScoreWeightRepository) Proxy.newProxyInstance(
                CompositeScoreWeightRepository.class.getClassLoader(),
                new Class<?>[]{CompositeScoreWeightRepository.class},
                (proxy, method, args) -> {
                    if ("findAll".equals(method.getName())) {
                        return store;
                    }
                    if ("save".equals(method.getName())) {
                        CompositeScoreWeight next = (CompositeScoreWeight) args[0];
                        store.clear();
                        store.add(next);
                        return next;
                    }
                    if ("toString".equals(method.getName())) {
                        return "CompositeScoreWeightRepositoryTestProxy";
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(method.getName())) {
                        return proxy == args[0];
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private void setRepository(CompositeScoreWeightService service, CompositeScoreWeightRepository repository) throws Exception {
        Field field = CompositeScoreWeightService.class.getDeclaredField("compositeScoreWeightRepository");
        field.setAccessible(true);
        field.set(service, repository);
    }
}
