package com.hmdp.loadtest;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 不依赖 Spring，纯 HTTP 秒杀压测（JDK8+）。多 token 轮询，模拟多用户。
 * <p>
 * 用法（在 IDEA 里对 main 右键 Run，或编译后）：
 * <pre>
 * java -cp ... com.hmdp.loadtest.SeckillLoadBench http://127.0.0.1:8081 10 32 2000 d:/path/to/tokens.txt
 * </pre>
 * 参数：baseUrl  voucherId  线程数  总请求数  token文件路径（每行一个 authorization）
 */
public final class SeckillLoadBench {

    private SeckillLoadBench() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 5) {
            System.err.println("Usage: SeckillLoadBench <baseUrl> <voucherId> <threads> <totalRequests> <tokensFile>");
            System.err.println("Example: SeckillLoadBench http://127.0.0.1:8081 10 32 2000 d:/tokens.txt");
            System.exit(1);
        }
        String base = args[0].replaceAll("/+$", "");
        long voucherId = Long.parseLong(args[1]);
        int threads = Integer.parseInt(args[2]);
        int total = Integer.parseInt(args[3]);
        String tokenFile = args[4];

        List<String> tokens = loadTokens(tokenFile);
        if (tokens.isEmpty()) {
            System.err.println("No tokens in file: " + tokenFile);
            System.exit(1);
        }

        String seckillUrl = base + "/voucher-order/seckill/" + voucherId;
        System.out.println("URL=" + seckillUrl + " threads=" + threads + " total=" + total + " tokenLines=" + tokens.size());

        AtomicInteger ok = new AtomicInteger();
        AtomicInteger biz = new AtomicInteger();
        AtomicInteger httpErr = new AtomicInteger();
        AtomicLong seq = new AtomicLong();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(total);
        long t0 = System.nanoTime();

        for (int i = 0; i < total; i++) {
            pool.submit(() -> {
                try {
                    String token = tokens.get((int) (Math.abs(seq.getAndIncrement()) % tokens.size()));
                    String cat = postSeckill(seckillUrl, token);
                    if ("ok".equals(cat)) {
                        ok.incrementAndGet();
                    } else if ("biz".equals(cat)) {
                        biz.incrementAndGet();
                    } else {
                        httpErr.incrementAndGet();
                    }
                } catch (Exception e) {
                    httpErr.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        pool.shutdownNow();
        double sec = (System.nanoTime() - t0) / 1_000_000_000.0;

        System.out.println("duration_sec: " + String.format("%.3f", sec));
        System.out.println("total: " + total);
        System.out.println("success_true~: " + ok.get());
        System.out.println("success_false (stock/dup/etc): " + biz.get());
        System.out.println("http/error: " + httpErr.get());
        System.out.println("approx_rps: " + String.format("%.1f", total / sec));
    }

    private static List<String> loadTokens(String path) throws Exception {
        List<String> lines = Files.readAllLines(Paths.get(path), StandardCharsets.UTF_8);
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) {
                continue;
            }
            out.add(t);
        }
        return out;
    }

    /**
     * @return ok | biz | http
     */
    private static String postSeckill(String urlStr, String authorization) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(30_000);
        conn.setReadTimeout(30_000);
        conn.setDoOutput(true);
        conn.setRequestProperty("authorization", authorization);
        conn.getOutputStream().close();
        int code = conn.getResponseCode();
        InputStream in = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        if (in == null) {
            return "http";
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String ln;
            while ((ln = br.readLine()) != null) {
                sb.append(ln);
            }
        }
        conn.disconnect();
        if (code != 200) {
            return "http";
        }
        String body = sb.toString();
        if (body.contains("\"success\"") && body.contains("true")) {
            return "ok";
        }
        if (body.contains("\"success\"") && body.contains("false")) {
            return "biz";
        }
        return "http";
    }
}
