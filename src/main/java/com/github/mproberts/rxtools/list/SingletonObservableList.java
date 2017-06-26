package com.github.mproberts.rxtools.list;

import rx.Observable;

import java.util.List;

class SingletonObservableList<T> implements ObservableList<T>
{
    private final Observable<Update<T>> _justReloadObservable;

    public SingletonObservableList(List<T> list)
    {
        _justReloadObservable = Observable.just(new Update<>(list, Change.reloaded()));
    }

    @Override
    public Observable<Update<T>> updates()
    {
        return _justReloadObservable;
    }
}