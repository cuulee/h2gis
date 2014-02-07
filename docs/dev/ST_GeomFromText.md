---
layout: docs
title: ST_GeomFromText
category: h2spatial/geometry-conversion
description: Well Known Text &rarr; <code>GEOMETRY</code>
prev_section: ST_AsText
next_section: ST_LineFromText
permalink: /docs/dev/ST_GeomFromText/
---

### Signatures

{% highlight mysql %}
GEOMETRY ST_GeomFromText(varchar wkt);
GEOMETRY ST_GeomFromText(varchar wkt, int srid);
{% endhighlight %}

### Description

Converts the Well Known Text `wkt` into a `GEOMETRY` with spatial reference id
`srid`.  The default value of `srid` is 0.

{% include z-coord-warning.html %}
{% include sfs-1-2-1.html %}

### Examples

{% highlight mysql %}
SELECT ST_GeomFromText('POINT(2 3)', 27572);
-- Answer: POINT (2 3)

SELECT ST_SRID(ST_GeomFromText('LINESTRING(1 3, 1 1, 2 1)'));
-- Answer: 0

SELECT ST_GeomFromText('POLYGON((0 0 -1, 2 0 2, 2 1 3, 0 0 -1))');
-- Answer: POLYGON ((0 0, 2 0, 2 1, 0 0))
{% endhighlight %}

##### See also

* <a href="https://github.com/irstv/H2GIS/blob/master/h2spatial/src/main/java/org/h2gis/h2spatial/internal/function/spatial/convert/ST_GeomFromText.java" target="_blank">Source code</a>