# Local code graph

For architecture or call-flow questions, first query the local Graphify index when `.tools/graphify-project/graphify-out/graph.json` exists. Keep queries narrow and verify important claims in the source; the graph is a navigation aid, not ground truth.

```powershell
$graph = '.tools/graphify-project/graphify-out/graph.json'
$cli = '.tools/graphify-tool/graphifyy/Scripts/graphify.exe'
$env:GRAPHIFY_QUERY_LOG_DISABLE = '1'
& $cli query 'how does batch queue save flow connect to the UI?' --graph $graph --budget 900 --depth 2
```

Use `graphify path` for a relationship between two named components. If the index is missing or stale, run `scripts/Update-ProjectGraph.ps1` when Graphify is installed, or use targeted source search. Do not index PDFs, Excel workbooks, demo documents, or other document contents; this local index is intentionally code-only. Graphify output stays under ignored `.tools/` and should not be committed.
