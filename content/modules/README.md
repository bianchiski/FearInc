# Module content format

`catalog.json` is the syllabus. Each module `items` entry is an item id that must exist at `content/items/<id>/item.json`, and every item on disk must be listed.

A `module.json` `problems` list is checked the same way. An id there must be a real item in that module.
