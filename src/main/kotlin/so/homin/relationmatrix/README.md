# RelationMatrix

A library that quickly calculates unique entities from very large relationship data.

ex)
```
SELECT domain, relation FROM relations;

|domain |relation
|-------|-------
|0      |1
|0      |4
|1      |2
|2      |3
|2      |4
|2      |5
...
```

```
SELECT DISTINCT relation FROM relations WHERE domain IN (0, 2, ...)

|relation
|-------
|1
|3
|4
|5
...
```
can be calculated like this:
```
val matrix = RelationMatrix.Builder()
    .add(0, intArrayOf(1, 4))
    .add(1, intArrayOf(2))
    .add(2, intArrayOf(3, 4, 5))
    ...
    .build()
```
```
val domain = MutableRoaringBitmap()
domain.add(0)
domain.add(2)
...

val relations = matrix.getUniqueRelations(domain)
```


