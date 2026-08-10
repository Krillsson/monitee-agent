ARG BASE_IMAGE
FROM ${BASE_IMAGE}

# jib writes the runtime classpath to an argument file. Reusing it verbatim is what makes the
# cache valid at runtime: an AOT cache is rejected unless the classpath string matches the one
# it was recorded with, so the entries cannot be re-listed or globbed here.
#
# Recording and creating happen in one layer because the intermediate configuration is larger
# than the cache itself. The training run needs a working directory of its own: the agent
# resolves its database and keystore relative to the current one and would otherwise leave
# them in /data.
RUN mkdir -p /tmp/aot && cd /tmp/aot \
    && java -server -Djava.awt.headless=true -XX:+UseSerialGC -Xmx512m -Xms128m \
        -XX:AOTMode=record -XX:AOTConfiguration=/tmp/app.aotconf \
        -Dspring.context.exit=onRefresh \
        -cp @/app/jib-classpath-file \
        com.krillsson.sysapi.SysAPIApplicationKt \
    && java -server -Djava.awt.headless=true -XX:+UseSerialGC -Xmx512m -Xms128m \
        -XX:AOTMode=create -XX:AOTConfiguration=/tmp/app.aotconf -XX:AOTCache=/app/app.aot \
        -cp @/app/jib-classpath-file \
    && rm -rf /tmp/aot /tmp/app.aotconf \
    && test -s /app/app.aot
