# http
A simple multi-threaded HTTP/1.0-ish file server. Single file, 300 LOC.

Handles only GET requests, serving files below the directory in which
the server is started.  Does Content-Type inference based on file
extensions (e.g. html, js, png, jpg).

Currently disables any type of cacheing as I use it mostly for local
development and want to avoid versioning mistakes.

Also has fledgling SSL support that has occasionally worked.

To run:
```
java net.http.Server
```

or if you can't run on priviledged ports (<1024), pick a high one:

```
java -Dport=8080 net.http.Server
java -Dport=8080 -Dssl=true net.http.Server
```

Good performance using Java's Native IO.  Loadtest using acme.com's http_load:

```
> cat /proc/cpuinfo
... Intel(R) Xeon(R) CPU           X5679  @ 3.20GHz ...
... Intel(R) Xeon(R) CPU           X5679  @ 3.20GHz ...
... Intel(R) Xeon(R) CPU           X5679  @ 3.20GHz ...
> dd if=/dev/zero of=10k.dat bs=1024 count=10
> echo "http://localhost:8080/10k.dat" > test.url
> ./http_load -p 10 -f 10000 test.url
# Throwaway
> ./http_load -p 10 -f 100000 test.url
100000 fetches, 10 max parallel, 1.024e+09 bytes, in 34.9176 seconds
10240 mean bytes/connection
2863.88 fetches/sec, 2.93262e+07 bytes/sec
msecs/connect: 0.0995282 mean, 2.717 max, 0.037 min
msecs/first-response: 3.18724 mean, 202.23 max, 0.315 min
HTTP response codes:
  code 200 -- 100000
```
