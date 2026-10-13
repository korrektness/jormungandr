package loading.specs

import loading.library.Counter

class CounterModel(val count: Int)

fun incrementModel(counter: Counter): CounterModel = CounterModel(counter.count + 1)
