# scalaz-stream

## Goals for tonight:

* Compare evolution of stream processing techniques
    * Java in Scala
    * Iterators
    * Play Iteratees
    * scalaz-stream
* Assert that the `z` is here to help, not confuse.

### IndyScala, December 8, 2014 -- Ross A. Baker, CrowdStrike

---

# This is part 2 of 3.

1. scalaz.concurrent.Task (November, 2014)
2. **scalaz-stream** (December, 2014)
3. http4s (???)

Fear not, I'll explain `Task` again when it comes up.

---

# Not in scope tonight:

* Make you an expert in scalaz-stream
* Make you intermediate in scalaz-stream

---

# Requirements

1. Parse a CSV file.
2. Discard the header and any malformed lines.
3. Geocode the IP from the CSV.
4. Filter for US.
5. Emit 20 records.
6. Run in constant space.

---

# Attempt #1: Java in Scala

    !scala
    val in = new BufferedReader(new FileReader(inF))
    try {
      val out = new PrintWriter(new FileWriter(outF), true)
      try {
        var line: String = in.readLine() // Skip the header
        var i = 20
        while ({ line = in.readLine(); line != null && !line.contains('"') && i > 0 }) {
          val mockData = MockData.parse(line)
          val country = Await.result(Geocoder(mockData.ipAddress), 5.seconds)
          if (country == Country("United States")) {
            out.println(mockData.email)
            i = i - 1
          }
        }
      } finally out.close()
    } finally in.close()

---

# Documenting the atrocities

* Manual, verbose resource management of input and output
* `null`.  Boo, hiss.
* Poorly factored
    * Termination condition entwined with mutable state.
    * Filtering pushes surviving lines into deeper nesting.
    * Repeats `readLine()` call to discard header.
* Blocks threads
    * On geocoder
    * On input
    * On output
* Arbitrary timeout on geocoder.
* Not parallel
* Flow of code hard to trace back to requirements.

---

# Attempt #2: More conventional Scala

    !scala
    using(new FileInputStream("mockaroo.csv")) { in =>
      using(new PrintWriter(new FileOutputStream("emails.txt"), true)) { out =>
        Source.fromInputStream(in).getLines
          .drop(1)
          .filterNot(_.contains('"'))
          .map(MockData.parse)
          .map(datum => datum -> Await.result(Geocoder(datum.ipAddress), 5.seconds))
          .collect { case (datum, Country(c)) if c == "United States" => datum.email }
          .take(20)
          .foreach(out.println)
      }
    }

---

# Reassessing the atrocities

* Manual, <del>verbose</del> resource management of input and output
* <del>`null`.  Boo, hiss.</del>
* <del>Poorly factored</del>
    * <del>Termination condition entwined with mutable state.</del>
    * <del>Filtering pushes surviving lines into deeper nesting.</del>
    * <del>Repeats `readLine()` call to discard header.</del>
* Blocks threads
    * On geocoder
    * On input
    * On output
* Arbitrary timeout on geocoder.
* Not parallel
* <del>Flow of code hard to trace back to requirements.</del>

---

# Going reactive with iteratees

* Scalaz has an alternate, mostly forgotten implementation.
* Should be familiar to you Play folks and old faces here.
* In ten words or less: an iterator pulls, an iteratee is pushed onto.
* Let's port our code to play-iteratee.

---

# Iteratee version

    !scala
    using(new PrintWriter(new FileWriter(outF), true)) { out =>
      val f = Enumerator.fromFile(inF)
        .through(decode())
        .map(new String(_))
        .through(Enumeratee.grouped(upToNewLine))
        .through(filter(_.nonEmpty))
        .through(drop(1))
        .through(filterNot[String](_.contains('"')))
        .through(map(MockData.parse))
        .through(mapM(datum => Geocoder(datum.ipAddress).map(datum -> _)))
        .through(collect { case (datum, Country(c)) if c == "United States" => datum.email})
        .through(take(20))
        .run(Iteratee.foreach(out.println))
      Await.result(f, 1.minute)
    }

---

# A giving and taking of atrocities

* Manual, <del>verbose</del> resource management of <del>input</del> and output
* Blocks threads
    * <del>On geocoder</del>
    * On input
    * On output
* <del>Arbitrary timeout on geocoder.</del>
* Not parallel
* <ins>Needs an "extras" package from a personal GitHub just to decode a binary stream to text in constant space.</ins>
* <ins>My attempts to parallelize led to buffer overflows and stack overflows</ins>
    * Possible mitigating factor: I'm not very smart.

---

# The guest of honor arrives

    !scala
    file.linesR(inF.getName)
      .drop(1)
      .filter(!_.contains('"'))
      .map(MockData.parse)
      .gatherMap(8)(datum => Geocoder.task(datum.ipAddress).map(datum -> _))
      .collect { case (datum, Country(c)) if c == "United States" => datum.email }
      .take(20)
      .to(file.chunkW(outF.getName).contramap(s => ByteVector(s.getBytes)))
      .run
      .run

---

# The end of the original atrocities

* <del>Manual, verbose resource management of input and output</del>
* <del>Blocks threads</del>
    * <del>On geocoder</del>
    * <del>On input</del>
    * <del>On output</del>
* <del>Arbitrary timeout on geocoder.</del>
* <del>Not parallel</del>
* <del>Needs an "extras" package from a personal GitHub just to decode a binary stream to text in constant space.</del>
* <del>My attempts to parallelize led to buffer overflows and stack overflows</del>
* <ins>scalaz-stream isn't in Maven Central</ins>
    * http://play.textadventures.co.uk/Play.aspx?id=zv-wer8keey6rnhk4am25q
* <ins>You're betting your project on 0.6 of something.</ins>
    * But I didn't change a line of code from 0.5 to 0.6.
* <ins>Task, rather than Future, is the first class citizen.</ins>
    * In time, you will view this as a selling point.

---

# Review: convert a Future to a Task

    !scala
    Task.async { f =>
      future.onComplete {
        case Success(a) => f(right(a))
        case Failure(t) => f(left(t))
      }
    }

---

# The core types

* A "Scalaz stream" is a `scalaz.stream.Process[F[_], O]`.
* The `F[_]` is the context in which an action runs.
    * It can be anything with one type parameter.
        * But it has to be a Monad to run.
        * It has to be `Catchable` to run.
* `O` is the type returned by the action.
* A `Process` is sequence of `F` actions returning `O`.

---

# `Process[Task, O]`

* A source of `O` is typically a `Process[Task, O]`.
    * `O`s are requested on demand.
* Lots of ways to create them:
    * `Process.apply`, `Process.emitAll`, etc. to source simple collections.
    * `scalaz.stream.io` and `scalaz.stream.nio` for I/O, blocking and not.
    * `scalaz.stream.tcp` for remoting.
    * `scalaz.stream.async` can generate a source from a queue, bounded or not.
    * `Process.eval` makes a one-element `Process` from a `Task`.
    * `Process.repeatEval` makes a source from repeatedly invoking a `Task`.
* This is like an `Enumerator[O]`.

---

# `Process1[I, O]`

* Alias for `Process[Env[I,Any]#Is, O]`
    * That `Is` is type system voodoo to ensure that an `I` is requested.
    * It translates inputs to zero-to-many outputs.
* `Process[F, A].pipe(Process[A, B]) => `Process[F, B]`
* `filter`, `drop`, `take` are all simple examples.
* This is like an `Enumeratee[I, O]`.
    * The common ones come with suffix notation to preserve the feel of a collection.

---

# `Sink[Task, I]`

* Alias for `Channel[Task, I, Unit]`
    * Alias for `Process[Task, I => Task[F[Unit]]`
    * It's a source of functions to be run on each input
* `Process[F, A].to(Sink[Task, A]) => Process[Task, Unit]`
* `scalaz.stream.io` and `scalaz.stream.nio` for I/O, blocking and not.
* This is like an `Iteratee[O, Unit]`
    * But more controlled, because Tasks don't run until you tell them.

---

# Running

* `def run: F[Unit]`: Run for a side effect.
* `def runLast: F[Option[A]]`: Discard all values but the last.
    * `def runLastOr(a: A): F[A]`: In case the process emits nothing
* `def runLog: F[Vector[A]]`: Get all the intermediate results
* `def runFoldMap(f: A => B): F[B]`: Map the outputs to `B`, and add them up.
    * `B` must have a monoid instance.
    * All the others are built on this.
* If `F` is a Task, nothing happens until you `run` the result of your `run`.
    * Banish the side effects to the outermost edge of your program.
    * Easy to compose `Process` results into larger execution flows.

---

# Jawn

* Jawn is a fast JSON parser, supporting a variety of JSON AST:
    * Argonaut
    * json4s
    * play-json
    * rojoma
    * spray-json
* It has an asynchronous mode, to return results from partial input

---

# Jawn-Streamz

* I wrote a simple scalaz-stream adapter
    * https://github.com/rossabaker/jawn-streamz
    * Is backbone of http4s' JSON support
    * Coming eventually: serialization.

---

# Jawn-Streamz example

    !scala
    Process.awakeEvery(1.second) // every 1 second
      .map(_ => 64) // ask for 64 bytes
      .through(file.chunkR("mockaroo.json")) // from mockaroo.json
      .unwrapJsonArray[JValue] // emit one JSON array element at a time
      .collect { case jObj: JObject => jObj.get("email") } // get the e-mail
      .map(_.toString) // convert to string
      .to(scalaz.stream.io.stdOutLines) // write to stdout
      .run // convert to a task
      .run // and execute said task

---

# In production

* We process hundreds of millions of messages a day through scalaz-stream
* Great fit for consuming from and producting to Kafka topics.
    * Manages our connections, which is more complicated than try-finally-close.
    * Very robust in bursty load
    * Open source wrappers on the way.
---

# About me

- [@rossabaker](http://twitter.com/rossabaker)
- Co-organizer, [IndyScala](http://github.com/indyscala/)
- Principal Cloud Engineer, [CrowdStrike](http://www.crowdstrike.com/)
    - We're [hiring](http://www.crowdstrike.com/about-us/careers/)
