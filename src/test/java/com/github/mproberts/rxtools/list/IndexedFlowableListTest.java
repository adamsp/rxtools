package com.github.mproberts.rxtools.list;

import com.github.mproberts.rxtools.types.Optional;
import io.reactivex.Flowable;
import io.reactivex.functions.BiFunction;
import io.reactivex.functions.Function;
import io.reactivex.functions.Function3;
import io.reactivex.subscribers.TestSubscriber;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class IndexedFlowableListTest
{
    protected TestSubscriber<Update<Flowable<String>>> createIndexedList(FlowableList<Integer> list)
    {
        FlowableList<Flowable<String>> transformedList = list.indexedMap(new Function3<Integer, Flowable<Optional<Integer>>, Flowable<Optional<Integer>>, Flowable<String>>() {
            @Override
            public Flowable<String> apply(final Integer item, Flowable<Optional<Integer>> previousItem, Flowable<Optional<Integer>> nextItem) throws Exception
            {
                return Flowable.combineLatest(previousItem, nextItem, new BiFunction<Optional<Integer>, Optional<Integer>, String>() {
                    @Override
                    public String apply(Optional<Integer> previous, Optional<Integer> next) throws Exception
                    {
                        String previousString = previous.map(new Function<Integer, String>() {
                            @Override
                            public String apply(Integer integer) throws Exception {
                                return integer.toString();
                            }
                        }).orElse("?");
                        String nextString = next.map(new Function<Integer, String>() {
                            @Override
                            public String apply(Integer integer) throws Exception {
                                return integer.toString();
                            }
                        }).orElse("?");

                        return previousString + " < " + item + " > " + nextString;
                    }
                });
            }
        });

        return transformedList.updates().test();
    }

    @Test
    public void testAddTransform()
    {
        SimpleFlowableList<Integer> list = new SimpleFlowableList<>(Arrays.asList(1, 2, 3));

        TestSubscriber<Update<Flowable<String>>> testSubscriber = createIndexedList(list);

        testSubscriber.assertValueCount(1);

        List<Update<Flowable<String>>> onNextEvents = testSubscriber.values();

        assertEquals(Arrays.asList(Change.reloaded()), onNextEvents.get(0).changes);

        List<Flowable<String>> list1 = onNextEvents.get(0).list;

        Flowable<String> item1 = list1.get(0);
        Flowable<String> item2 = list1.get(1);
        Flowable<String> item3 = list1.get(2);

        TestSubscriber<String> test1 = item1.test();
        TestSubscriber<String> test2 = item2.test();
        TestSubscriber<String> test3 = item3.test();

        test1.assertValue("? < 1 > 2");
        test2.assertValue("1 < 2 > 3");
        test3.assertValue("2 < 3 > ?");

        list.add(1, 4);

        test1.assertValues("? < 1 > 2", "? < 1 > 4");
        test2.assertValues("1 < 2 > 3", "4 < 2 > 3");
        test3.assertValueCount(1);

        list.add(5);

        test1.assertValues("? < 1 > 2", "? < 1 > 4");
        test2.assertValues("1 < 2 > 3", "4 < 2 > 3");
        test3.assertValues("2 < 3 > ?", "2 < 3 > 5");
    }

    @Test
    public void testRemoveTransform()
    {
        SimpleFlowableList<Integer> list = new SimpleFlowableList<>(Arrays.asList(1, 2, 3, 4));

        TestSubscriber<Update<Flowable<String>>> testSubscriber = createIndexedList(list);

        testSubscriber.assertValueCount(1);

        List<Update<Flowable<String>>> onNextEvents = testSubscriber.values();

        assertEquals(Arrays.asList(Change.reloaded()), onNextEvents.get(0).changes);

        List<Flowable<String>> list1 = onNextEvents.get(0).list;

        Flowable<String> item1 = list1.get(0);
        Flowable<String> item2 = list1.get(1);
        Flowable<String> item3 = list1.get(2);
        Flowable<String> item4 = list1.get(3);

        TestSubscriber<String> test1 = item1.test();
        TestSubscriber<String> test2 = item2.test();
        TestSubscriber<String> test3 = item3.test();
        TestSubscriber<String> test4 = item4.test();

        test1.assertValue("? < 1 > 2");
        test2.assertValue("1 < 2 > 3");
        test3.assertValue("2 < 3 > 4");
        test4.assertValue("3 < 4 > ?");

        list.remove(3);

        test3.assertValues("2 < 3 > 4", "2 < 3 > ?");

        list.remove(1);

        test1.assertValues("? < 1 > 2", "? < 1 > 3");
        test3.assertValues("2 < 3 > 4", "2 < 3 > ?", "1 < 3 > ?");
    }

    @Test
    public void testMoveTransform()
    {
        SimpleFlowableList<Integer> list = new SimpleFlowableList<>(Arrays.asList(1, 2, 3, 4));

        TestSubscriber<Update<Flowable<String>>> testSubscriber = createIndexedList(list);

        testSubscriber.assertValueCount(1);

        List<Update<Flowable<String>>> onNextEvents = testSubscriber.values();

        assertEquals(Arrays.asList(Change.reloaded()), onNextEvents.get(0).changes);

        List<Flowable<String>> list1 = onNextEvents.get(0).list;

        Flowable<String> item1 = list1.get(0);
        Flowable<String> item2 = list1.get(1);
        Flowable<String> item3 = list1.get(2);
        Flowable<String> item4 = list1.get(3);

        TestSubscriber<String> test1 = item1.test();
        TestSubscriber<String> test2 = item2.test();
        TestSubscriber<String> test3 = item3.test();
        TestSubscriber<String> test4 = item4.test();

        test1.assertValue("? < 1 > 2");
        test2.assertValue("1 < 2 > 3");
        test3.assertValue("2 < 3 > 4");
        test4.assertValue("3 < 4 > ?");

        list.move(3, 1);

        test1.assertValues("? < 1 > 2", "? < 1 > 4");
        test2.assertValues("1 < 2 > 3", "4 < 2 > 3");
        test3.assertValues("2 < 3 > 4", "2 < 3 > ?");

        list.move(1, 3);

        test1.assertValues("? < 1 > 2", "? < 1 > 4", "? < 1 > 2");
        test2.assertValues("1 < 2 > 3", "4 < 2 > 3", "1 < 2 > 3");
        test3.assertValues("2 < 3 > 4", "2 < 3 > ?", "2 < 3 > 4");
    }
}
