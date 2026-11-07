package com.yoshione.fingen.interfaces;

import io.reactivex.rxjava3.disposables.Disposable;

public interface IUnsubscribeOnDestroy {
    void unsubscribeOnDestroy(Disposable disposable);
}
