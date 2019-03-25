package my.android.inappbilling.utils

import android.annotation.SuppressLint
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.Action
import io.reactivex.functions.Function
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit

class RxUtils {
    companion object {
        @SuppressLint("CheckResult")
        fun <T> runInBack(process:()->T, onNext: ((T) -> Unit)? = null, complete:()->Unit){
            Observable.fromCallable(process)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ onNext?.invoke(it) }, { it?.printStackTrace() }, { complete() })
        }

        @SuppressLint("CheckResult")
        fun <T> iterateInBack(iterable:Iterable<T>, onNext: ((item:T) -> Unit)?=null, complete: () -> Unit){
            Observable.fromIterable(iterable)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ onNext?.invoke(it) }, { it?.printStackTrace() }, { complete() })
        }

        @SuppressLint("CheckResult")
        fun <I,O> iterateInBack(iterable:Iterable<I>, onNext: Function<I,O>?=null, complete: Action?=null){
            Observable.fromIterable(iterable)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ onNext?.apply(it) }, { it?.printStackTrace() }, { complete?.run() })
        }

        fun then(){

        }

        @SuppressLint("CheckResult")
        fun withDelay(retryMilliSecond:Long, bool:Boolean, onNext: (() -> Unit)? = null, complete:()->Unit){
            Observable.interval(retryMilliSecond, TimeUnit.MILLISECONDS )
                    .retryWhen { Observable.just(!bool) }
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ onNext?.invoke() }, { it?.printStackTrace() }, { complete() })
        }
    }
}