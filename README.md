
# Possible Improvements of Test dependencies

## Refinement - Better capture

The current implementation is not optimized regarding memory usage and it has memory leaks.
This can be seen by observing that the collection time increases with time (and collected deps).
One possible cause is that we *snag* the value of objects inside XML Strings, and never deallocate resources.

Collecting actual values is not really necessary to discover test data dependencies: for that is enough to observe conflicting reads/writes and discard whatever value/structure of the objects involved in the dep.

Additionally, the algorithm to collect data dependency might be optimized. At the moment, we pass through all the classes loaded at the end of each test to look for reachable fields. Smart indices and static analysis might be used to speed up the search.

## Refinement - Less abstraction

During refinement, abstracting the way test depends on each other (i.e., fields and variable causing the dependency) lead to under/over approximations. By tracking in more detail what cause a dependency we might be able to improve the current results by either speeding up refinement, identifying more dependencies, or both. For example, tests with more than one ancestor setting a different set of variables.

On different terms, one might want to track write and read set of tests to enable state restoring (actually we might require some test carving here to reset the state by re-invoking some actions, instead of forcing the state which does not work when external resources --file descriptors for example-- are involved.

## Refinement - Additional Repetitions

Manifest dependencies cannot be exposed if the corresponding data dependencies are not collected. This requires running (some) tests in different orders. Identify the tests to run and the additional orders to follow is an open point since there is little evidence --a priori-- that they will lead to discovering any dependency. A possible way to deal with this is to observe flaky tests during refinement, recover the order which lead to that, and try to repeat the same while collecting dependencies. The results for the collection will be used to add new edges in the test dependency graph, meaning more data dependencies to test. Alternatively, one might design some specific static analysis to define the set of potentially conflicting tests (computing the set of potential write/read for each test).


## Incremental Identification of Test Dependencies
In the context of regression testing, test dependencies are still relevant.
The actual solution does not consider regression (i.e., local/partial changes) at all, hence it requires to rerun the whole data dependency collection and refine at each code commit. This might not be necessary a problem, since one can assume that test dependencies might not be introduced frequently and repeat the analysis once a while, and not always. However, when stringent constraints on quality are needed, a different approach, which incrementally handles the changes is required.

A solution might be to abstract the state of the application at the very end of the previous test execution (full test collection). Then, execute only the new tests or the tests involving modified classes using that abstracted state as the starting point. Basically, the abstraction will list the write set at the end of the previous test execution: reload all the classes and set the write attribute to them.
Next, update the dependency graph and remove from it all the already-tested as well as the deps unchanged in new commit. Finally, repeat the refinement from that point.

This solution has the following problems: this approach works but only if we do not change the application code. In fact, re-setting the whole state assumes that is the same as re-executing the test suite, which is possible only if nothing changes in the meanwhile. Of course, this is not really representative of typical regression scenarios which include adding and removing tests as well as updating application code and tests.

To account for this, we might broaden the scope of the approach and assume that to know which tests are not valid anymore, and which other tests must re-run. With this information the challenge is to update the previous test dependency graph without necessarily re-execute the whole test suite. Ideally, we shall execute only a subset of tests, which are the one deemed relevant by test suite regression selection algorithms (such as Ekstazi). This of course under the assumption that rerunning the full collection is more expensive than identify the tests to re-execute and update the dependency graph.

Updates to test dependency graph must consider old tests that are not valid anymore, hence their write sets shall be invalidated, old tests that changed, hence their read and write sets must be updated, new tests that might introduce additional edges. A side challenge is to identify the "new" order of execution of the test suite.