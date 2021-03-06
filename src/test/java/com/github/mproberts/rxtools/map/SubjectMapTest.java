package com.github.mproberts.rxtools.map;

import com.github.mproberts.rxtools.map.SubjectMap;
import io.reactivex.Flowable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.subscribers.DisposableSubscriber;
import io.reactivex.subscribers.TestSubscriber;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class SubjectMapTest
{
    private static final int runs = 100000;
    private static final int loop = 50;
    private static final String[] keys = new String[loop];

    static {
        for (int i = 0; i < loop; ++i) {
            keys[i] = "run-" + i;
        }
    }

    private CompositeDisposable _subscription;
    private SubjectMap<String, Integer> source;

    private static class IncrementingFaultSatisfier<K> implements Consumer<K>
    {
        private final AtomicInteger _counter;
        private final SubjectMap<K, Integer> _source;

        IncrementingFaultSatisfier(SubjectMap<K, Integer> source, AtomicInteger counter)
        {
            _source = source;
            _counter = counter;
        }

        @Override
        public void accept(K key)
        {
            _source.onNext(key, _counter.incrementAndGet());
        }
    }

    private <T> void subscribe(Flowable<T> observable, DisposableSubscriber<T> action)
    {
        _subscription.add(observable.subscribeWith(action));
    }

    private <T> void subscribe(Flowable<T> observable, TestSubscriber<T> action)
    {
        _subscription.add(observable.subscribeWith(action));
    }

    private <T> void subscribe(Flowable<T> observable, Consumer<T> action)
    {
        _subscription.add(observable.subscribe(action));
    }

    private void unsubscribeAll()
    {
        _subscription.clear();
    }

    @Before
    public void setup()
    {
        source = new SubjectMap<>();
        _subscription = new CompositeDisposable();
    }

    @After
    public void teardown()
    {
        unsubscribeAll();
    }

    @Test
    public void testQueryAndIncrementOnFault()
    {
        // setup
        AtomicInteger counter = new AtomicInteger(0);
        Disposable faultSubscription = source.faults()
                .subscribe(new IncrementingFaultSatisfier<>(source, counter));

        TestSubscriber<Integer> testSubscriber1 = new TestSubscriber<>();
        TestSubscriber<Integer> testSubscriber2 = new TestSubscriber<>();
        TestSubscriber<Integer> testSubscriber3 = new TestSubscriber<>();

        subscribe(source.get("hello"), testSubscriber1);
        System.gc();

        testSubscriber1.assertValues(1);

        subscribe(source.get("hello"), testSubscriber2);
        System.gc();

        testSubscriber1.assertValues(1);
        testSubscriber2.assertValues(1);

        unsubscribeAll();
        System.gc();

        subscribe(source.get("hello"), testSubscriber3);

        testSubscriber3.assertValues(2);

        // cleanup
        faultSubscription.dispose();
    }

    @Test
    public void testBattlingSubscribers1() throws InterruptedException
    {
        TestSubscriber<Integer> testSubscriber1 = new TestSubscriber<>();
        TestSubscriber<Integer> testSubscriber2 = new TestSubscriber<>();

        Flowable<Integer> value1 = source.get("hello");
        Disposable s1 = value1.subscribeWith(testSubscriber1);

        source.onNext("hello", 3);

        s1.dispose();

        testSubscriber1.assertValues(3);

        Flowable<Integer> value2 = source.get("hello");
        Disposable s2 = value2.subscribeWith(testSubscriber2);
        Disposable s3 = value1.subscribeWith(testSubscriber1);

        source.onNext("hello", 4);

        s2.dispose();
        s3.dispose();

        testSubscriber2.assertValues(3, 4);
    }

    @Test
    public void testBattlingSubscribers() throws InterruptedException
    {
        // setup
        AtomicInteger counter = new AtomicInteger(0);
        Disposable faultSubscription = source.faults()
                .subscribe(new IncrementingFaultSatisfier<>(source, counter));

        Flowable<Integer> retainedObservable = source.get("hello");
        TestSubscriber<Integer> testSubscriber1 = new TestSubscriber<>();
        TestSubscriber<Integer> testSubscriber2 = new TestSubscriber<>();

        Disposable s1 = retainedObservable.subscribeWith(testSubscriber1);
        Disposable s2;

        testSubscriber1.assertValues(1);

        s1.dispose();

        testSubscriber1.assertValues(1);

        Flowable<Integer> retainedObservable2 = source.get("hello");

        s1 = retainedObservable.subscribeWith(testSubscriber1);

        testSubscriber1.assertValues(1);

        s2 = retainedObservable.subscribeWith(testSubscriber2);

        testSubscriber1.assertValues(1);

        s1.dispose();
        s2.dispose();

        // cleanup
        faultSubscription.dispose();
    }

    public void testMissHandling()
    {
        // setup
        final AtomicBoolean missHandlerCalled = new AtomicBoolean(false);
        AtomicInteger counter = new AtomicInteger(0);
        Disposable faultSubscription = source.faults()
                .subscribe(new IncrementingFaultSatisfier<>(source, counter));

        TestSubscriber<Integer> testSubscriber1 = new TestSubscriber<>();

        subscribe(source.get("hello"), testSubscriber1);

        source.onNext("hello2", new Callable<Integer>() {
            @Override
            public Integer call() throws Exception
            {
                fail("Value should not be accessed");
                return 13;
            }
        }, new Action() {
            @Override
            public void run()
            {
                missHandlerCalled.set(true);
            }
        });

        assertTrue(missHandlerCalled.get());

        testSubscriber1.assertValues(1);

        // cleanup
        faultSubscription.dispose();
    }

    @Test
    public void testErrorHandlingInValueProvider()
    {
        // setup
        final AtomicBoolean missHandlerCalled = new AtomicBoolean(false);
        TestSubscriber<Integer> testSubscriber1 = new TestSubscriber<>();

        subscribe(source.get("hello"), testSubscriber1);

        source.onNext("hello", new Callable<Integer>() {
            @Override
            public Integer call() throws Exception
            {
                throw new RuntimeException("Boom");
            }
        }, new Action() {
            @Override
            public void run()
            {
                missHandlerCalled.set(true);
            }
        });

        testSubscriber1.assertError(RuntimeException.class);
    }

    @Test
    public void testQueryAndUpdate()
    {
        // setup
        AtomicInteger counter = new AtomicInteger(0);
        Disposable faultSubscription = source.faults()
                .subscribe(new IncrementingFaultSatisfier<>(source, counter));

        TestSubscriber<Integer> testSubscriber1 = new TestSubscriber<>();
        TestSubscriber<Integer> testSubscriber2 = new TestSubscriber<>();
        TestSubscriber<Integer> testSubscriber3 = new TestSubscriber<>();

        subscribe(source.get("hello"), testSubscriber1);
        System.gc();

        testSubscriber1.assertValues(1);

        subscribe(source.get("hello"), testSubscriber2);
        System.gc();

        testSubscriber1.assertValues(1);
        testSubscriber2.assertValues(1);

        // send 10 to 2 already bound subscribers
        source.onNext("hello", 10);

        subscribe(source.get("hello"), testSubscriber3);

        // new subscriber 3 should only received the latest value of 10
        testSubscriber1.assertValues(1, 10);
        testSubscriber2.assertValues(1, 10);
        testSubscriber3.assertValues(10);

        // all 3 subscribers should receive the new value of 11
        source.onNext("hello", 11);

        testSubscriber1.assertValues(1, 10, 11);
        testSubscriber2.assertValues(1, 10, 11);
        testSubscriber3.assertValues(10, 11);

        // cleanup
        faultSubscription.dispose();
    }

    @Test
    public void testExceptionHandlingFault()
    {
        // setup
        final AtomicBoolean exceptionEncountered = new AtomicBoolean(false);
        final AtomicInteger counter = new AtomicInteger(0);
        Disposable faultSubscription = source.faults()
                .subscribe(new Consumer<String>() {
                    @Override
                    public void accept(String key) {
                        if (counter.incrementAndGet() <= 1) {
                            throw new RuntimeException("Explosions!");
                        }

                        source.onNext(key, counter.get());
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) {
                        assertTrue(throwable instanceof RuntimeException);

                        exceptionEncountered.set(true);
                    }
                });

        TestSubscriber<Integer> testSubscriber1 = new TestSubscriber<>();
        Flowable<Integer> helloValue = source.get("hello");

        subscribe(helloValue, testSubscriber1);

        testSubscriber1.assertNoValues();

        assertTrue(exceptionEncountered.get());

        // cleanup
        faultSubscription.dispose();
    }

    @Test
    public void testPruningUnsubscribedObservables()
    {
        // setup
        AtomicInteger counter = new AtomicInteger(0);
        Disposable faultSubscription = source.faults()
                .subscribe(new IncrementingFaultSatisfier<>(source, counter));

        TestSubscriber<Integer> testSubscriber = new TestSubscriber<>();

        @SuppressWarnings("unused")
        Flowable<Integer> helloValue = source.get("hello");

        helloValue = null;

        System.gc();

        subscribe(source.get("hello"), testSubscriber);

        testSubscriber.assertValues(1);

        source.onNext("hello", 11);

        testSubscriber.assertValues(1, 11);

        // cleanup
        faultSubscription.dispose();
    }

    @Test
    public void testSendBatchOfNoopsForUnobservedKey()
    {
        for (int i = 0; i < runs; ++i) {
            source.onNext(keys[i % 10], i);
        }
    }

    @Test
    public void testQueryBatchOfKeys()
    {
        final AtomicInteger counter = new AtomicInteger(0);

        for (int i = 0; i < loop; ++i) {
            final int index = i;

            keys[i] = "run-" + i;

            subscribe(source.get(keys[i]), new DisposableSubscriber<Integer>() {
                @Override
                public void onComplete()
                {
                    fail("Unexpected completion on observable");
                }

                @Override
                public void onError(Throwable e)
                {
                    fail("Unexpected error on observable");
                }

                @Override
                public void onNext(Integer value)
                {
                    assertEquals(index, value % 10);

                    counter.incrementAndGet();
                }
            });
        }

        for (int i = 0; i < runs; ++i) {
            source.onNext(keys[i % 10], i);
        }

        assertEquals(runs, counter.get());
    }

    @Test
    public void testThrashSubscriptions() throws InterruptedException, ExecutionException
    {
        final AtomicInteger globalCounter = new AtomicInteger(0);
        final int subscriberCount = 50;
        ExecutorService executorService = Executors.newFixedThreadPool(subscriberCount);

        for (int j = 0; j < 50; ++j) {
            System.gc();

            final AtomicInteger counter = new AtomicInteger(0);
            final Flowable<Integer> valueObservable = source.get("test");

            final Callable<Disposable> queryCallable = new Callable<Disposable>() {

                final int index = globalCounter.incrementAndGet();

                @Override
                public Disposable call() throws Exception
                {
                    return valueObservable.subscribe(new Consumer<Integer>() {
                        @Override
                        public void accept(Integer integer)
                        {
                            counter.incrementAndGet();
                        }
                    });
                }
            };
            List<Callable<Disposable>> callables = new ArrayList<>();

            for (int i = 0; i < subscriberCount; ++i) {
                callables.add(queryCallable);
            }

            List<Future<Disposable>> futures = executorService.invokeAll(callables);
            List<Disposable> subscriptions = new ArrayList<>();

            for (int i = 0; i < subscriberCount; ++i) {
                subscriptions.add(futures.get(i).get());
            }

            source.onNext("test", 1);

            for (int i = 0; i < 10; ++i) {
                if (counter.get() != subscriberCount) {
                    Thread.sleep(10);
                }
            }

            assertEquals(subscriberCount, counter.get());

            for (int i = 0; i < subscriberCount; ++i) {
                Disposable subscription = subscriptions.get(i);

                subscription.dispose();
            }
        }
    }

    @Test
    public void testThrashQuery() throws InterruptedException, ExecutionException
    {
        final int subscriberCount = 50;
        ExecutorService executorService = Executors.newFixedThreadPool(subscriberCount);

        for (int j = 0; j < 10; ++j) {
            System.gc();

            final AtomicInteger counter = new AtomicInteger(0);

            Callable<Flowable<Integer>> queryCallable = new Callable<Flowable<Integer>>() {
                @Override
                public Flowable<Integer> call() throws Exception
                {
                    return source.get("test");
                }
            };
            List<Callable<Flowable<Integer>>> callables = new ArrayList<>();

            for (int i = 0; i < subscriberCount; ++i) {
                callables.add(queryCallable);
            }

            List<Future<Flowable<Integer>>> futures = executorService.invokeAll(callables);

            for (int i = 0; i < subscriberCount; ++i) {
                Flowable<Integer> observable = futures.get(i).get();

                subscribe(observable, new Consumer<Integer>() {
                    @Override
                    public void accept(Integer value)
                    {
                        counter.incrementAndGet();
                    }
                });
            }

            source.onNext("test", 1);

            for (int i = 0; i < 10; ++i) {
                if (counter.get() != subscriberCount) {
                    Thread.sleep(10);
                }
            }

            assertEquals(subscriberCount, counter.get());

            unsubscribeAll();
        }
    }

    @Test
    public void testErrorEmission()
    {
        // setup
        AtomicInteger counter = new AtomicInteger(0);
        Disposable faultSubscription = source.faults()
                .subscribe(new IncrementingFaultSatisfier<>(source, counter));

        TestSubscriber<Integer> testSubscriber1 = new TestSubscriber<>();
        TestSubscriber<Integer> testSubscriber2 = new TestSubscriber<>();
        TestSubscriber<Integer> testSubscriber3 = new TestSubscriber<>();

        subscribe(source.get("hello"), testSubscriber1);
        System.gc();

        testSubscriber1.assertValues(1);

        subscribe(source.get("hello"), testSubscriber2);
        System.gc();

        testSubscriber1.assertValues(1);
        testSubscriber2.assertValues(1);

        // send error to 2 already bound subscribers
        RuntimeException error = new RuntimeException("whoops");
        source.onError("hello", error);

        subscribe(source.get("hello"), testSubscriber3);

        testSubscriber1.assertError(error);
        testSubscriber2.assertError(error);

        // new subscriber 3 should fault in a new value after the error
        testSubscriber3.assertValues(2);

        // cleanup
        faultSubscription.dispose();
    }
}
