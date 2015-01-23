maestro
=======

## Sub-projects

The directory structure of maestro code is intended to make certain development
tasks easier. Specifically, we want to be able to:

 1. Use _and_ test the macros for generating support from thrift classes.
 2. Achieve a fast development lifecycle.

The project structure comprises several directories:

 - `core` contains all the base data structures, tasks and utilities that are 
   used internally by maestro.
 - `macros` contains macro definitions for fields and codecs, and depends on
   functionality contained within `core`.
 - `api` wraps up and re-exports `core` + `macros` in a stable and consistent
   external API.
 - `example` contains end-to-end usage examples of maestro. New functionality
   should be added to the examples, stubbed out and filled in top-down from
   `core/macros/api`.
 - `benchmark` is useful for micro-benchmarks, this has been important for 
   working on codecs in particular.


## Development lifecycle

A lot of time is wasted doing cluster testing. With the right process, a round
trip can be achieved in about two minutes:

 1. Configure a mirror of the code on target (possibly local) server.
 2. Push code to the mirror.
 3. Use `example/bin/update-cluster`, `example/bin/build-cluster` and 
   `example/bin/test-cluster` to run through a test loop. The most common
    scenario is to run all three:

```
./maestro-example/bin/update-cluster && ./maestro-example/bin/build-cluster && ./maestro-example/bin/test-cluster
```

Currently these scripts are fairly specific to repository name (it is ~:repo,
which is useful for generic dev on EMR) and work area, but after they see a 
bit more use, the hope is that the scripts can become more robust and build out
a more resonable dev lifecycle.


## TODOs

 - Partition.byFields* needs to be cleaned up. The goal is sane error messages,
   at the moment that is only achievable by the non-overloaded, duplicated
   versions. It might be worth def macro a single version and writing a custom
   error message.
 - Clean-up TemplateParquetSource and move to ebenezer.
 - Add tests around error handling.
 - Autoify the macro codecs via MacroSupport, requires finding work around for
   macro-paradise bug on 2.10.3
 - Revisit DecodeMacro, and tidy up by trying to pull out for comprehension
   pattern
 - Fix perf of Fields, by using vampire methods trick to get rid of structural
   type
 - Add a CascadeJob base class to work around scalding bug w.r.t. validating
   cascades

