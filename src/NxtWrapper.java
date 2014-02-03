import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class NxtWrapper extends Nxt {

    public NxtWrapper() {
        super();
    }

    @Override
    public void init(javax.servlet.ServletConfig servletConfig) {
        Crypto.getPublicKey("");
        try {
            super.init(servletConfig);
        } catch (ServletException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void doGet(javax.servlet.http.HttpServletRequest req, javax.servlet.http.HttpServletResponse resp) throws ServletException, IOException {
        String requestType = req.getParameter("requestType");
        JSONObject response = new JSONObject();
        if (requestType.equals("listAccounts")) {
            for (Map.Entry<Long, Nxt.Account> entry : Nxt.accounts.entrySet()) {
                Account account = entry.getValue();
                AtomicReference<byte[]> publicKey = account.publicKey;
                byte[] publicBytes = publicKey.get();
                System.out.printf("Account %d balance %d effective balance %d public key %d\n",
                        entry.getKey(), account.getBalance(), account.getEffectiveBalance(),
                        publicBytes == null ? null : Account.getId(publicBytes));
                response.put("Id", entry.getKey());
                response.put("balance", account.getBalance());
                response.put("guaranteed", account.getGuaranteedBalance(10));
                response.put("CalculatedId", publicBytes == null ? null : Account.getId(publicBytes));
            }
            resp.setContentType("text/plain; charset=UTF-8");
            ServletOutputStream servletOutputStream = resp.getOutputStream();
            servletOutputStream.write(response.toString().getBytes("UTF-8"));
            servletOutputStream.close();
            return;
        }
        if (requestType.equals("listAccount")) {
            long id = Long.parseLong(req.getParameter("id"));
            Account account = accounts.get(id);
            AtomicReference<byte[]> publicKey = account.publicKey;
            byte[] publicBytes = publicKey.get();
            System.out.printf("Account %d balance %d effective balance %d public key %d\n",
                    id, account.getBalance(), account.getEffectiveBalance(),
                    publicBytes == null ? null : Account.getId(publicBytes));
            response.put("Id", id);
            response.put("balance", account.getBalance());
            response.put("guaranteed", account.getGuaranteedBalance(10));
            response.put("CalculatedId", publicBytes == null ? null : Account.getId(publicBytes));
            resp.setContentType("text/plain; charset=UTF-8");
            ServletOutputStream servletOutputStream = resp.getOutputStream();
            servletOutputStream.write(response.toString().getBytes("UTF-8"));
            servletOutputStream.close();
            return;
        }
        if (requestType.equals("logAccounts")) {
            for (Map.Entry<Long, Nxt.Account> entry : Nxt.accounts.entrySet()) {
                Account account = entry.getValue();
                AtomicReference<byte[]> publicKey = account.publicKey;
                byte[] publicBytes = publicKey.get();
                System.out.printf("Account %d balance %d effective balance %d public key %d\n",
                        entry.getKey(), account.getBalance(), account.getEffectiveBalance(),
                        publicBytes == null ? null : Account.getId(publicBytes));
            }
            return;
        }
        if (!requestType.equals("getNewData")) {
            System.out.printf("doGet from %s", req.getRemoteHost());
            Map<String, String[]> params = req.getParameterMap();
            for (Map.Entry<String, String[]> param : params.entrySet()) {
                System.out.printf("%s=%s\n", param.getKey(), param.getValue()[0]);
            }
        }
        super.doGet(req, resp);
        if (!requestType.equals("getNewData")) {
            logUsers();
        }
    }

    @Override
    public void doPost(javax.servlet.http.HttpServletRequest req, javax.servlet.http.HttpServletResponse resp) throws ServletException, IOException {
        System.out.printf("doPost from %s", req.getRemoteHost());
        Map<String, String[]> params = req.getParameterMap();
        for (Map.Entry<String, String[]> param : params.entrySet()) {
            System.out.printf("%s=%s\n", param.getKey(), param.getValue()[0]);
        }
        super.doPost(req, resp);
    }

    @Override
    public void destroy() {
        super.destroy();
        logUsers();
    }

    private void logUsers() {
        Map<String, User> users = Nxt.users;
        for (Map.Entry<String, User> entry : users.entrySet()) {
            System.out.printf("user %s secret %s\n", entry.getKey(), entry.getValue().secretPhrase);
        }
    }
}
