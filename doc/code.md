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


