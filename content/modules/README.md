# Module content format

`catalog.json` is the syllabus and the only source of item membership. Each module
`items` entry must exist at `content/items/<id>/item.json`, and every item on disk
must be listed exactly once.

`module.json` is optional module-level teaching content. Its `lessons` entries are
not a second item registry; problem metadata belongs in each item file.
