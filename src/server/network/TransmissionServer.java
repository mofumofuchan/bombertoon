package server.network;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import common.Score;
import server.constants.Direction;
import server.data.FieldServer;
import server.data.PlayerServer;

public class TransmissionServer extends Thread {
    private int member; // 接続しているメンバーの数

    public static final int PORT = 20000;
    public static final int MAX_BOMB = 3;
    public static final int MAX_PLAYER = common.Setting.N_PLAYERS;

    private Socket[] incoming; // 受付用のソケット
    private InputStreamReader[] isr; // 入力ストリーム用の配列
    private BufferedReader[] in; // バッファリングによりテキスト読み込み用の配列
    private PrintWriter[] out; // 出力ストリーム用の配列
    private ClientProcThread[] myClientProcThread; // スレッド用の配列

    private Direction[] direction;
    private int[] bombs;

    private static TransmissionServer instance;

    private boolean isBattling = false; // 戦闘中かを示す

    // constructor
    private TransmissionServer() {
        incoming = new Socket[MAX_PLAYER];
        isr = new InputStreamReader[MAX_PLAYER];
        in = new BufferedReader[MAX_PLAYER];
        out = new PrintWriter[MAX_PLAYER];
        myClientProcThread = new ClientProcThread[MAX_PLAYER];

        member = 0; // 誰も接続していないのでメンバー数は０
        direction = new Direction[MAX_PLAYER]; // ユーザごとのキー入力を保管
        for (int i = 0; i < MAX_PLAYER; i++) // 初期化
            direction[i] = Direction.NONE;
        bombs = new int[MAX_PLAYER];
    }

    public static TransmissionServer createInstance() {
        instance = new TransmissionServer();
        return instance;
    }

    public static TransmissionServer getInstance() {
        if (instance == null)
            createInstance();
        return instance;
    }

    // check
    public Direction checkHuman(int playerID) { // IDの人の動きを取得
        Direction res = direction[playerID];
        if (res != Direction.NONE) {
            direction[playerID] = Direction.NONE;
            // TODO debug
            System.out.println("checkHuman:" + playerID + ":" + res.name());
        } else {
            // TODO debug
            // System.out.println("checkHuman running");
        }
        return res;
    }

    public boolean checkBomb(int playerID) {
        if (bombs[playerID] == 0) {
            return false;
        } else {
            bombs[playerID]--;
            return true;
        }
    }

    // announce
    public void announceHuman(PlayerServer human) {
        for (int i = 0; i < MAX_PLAYER; i++) {
            String str;
            if (human.isDeath()) {
                str = "DEAD";
            } else {
                str = "ALIVE";
            }

            out[i].println("PLAYER:" + human.playerID + ":" + human.x + ":" + human.y + ":"
                    + human.dir.name() + ":" + str);
            out[i].flush(); // バッファをはき出す＝＞バッファにある全てのデータをすぐに送信する
        }
    }

    // フィールドの変化をクライアントに知らせる
    public void announceChangeField(FieldServer field) {
        System.out.println("ACF");
        for (int i = 0; i < MAX_PLAYER; i++) {
            if (field.isExistBomb) { // もしisExistBombがtrueなら
                out[i].println("FIELD:" + field.x + ":" + field.y + ":" + "BOMB" + ":"
                        + field.color.name());
            } else {
                out[i].println("FIELD:" + field.x + ":" + field.y + ":" + field.status.name() + ":"
                        + field.color.name());
            }
            out[i].flush(); // バッファをはき出す＝＞バッファにある全てのデータをすぐに送信する
        }
    }

    // プレイヤーが揃ったことをクライエントに知らせる
    // ゲームの（残り）時間を告知
    public void announceTime(int time) {
        for (int i = 0; i < MAX_PLAYER; i++) {
            out[i].println("TIME:" + time);
            out[i].flush(); // バッファをはき出す＝＞バッファにある全てのデータをすぐに送信する
        }
    }

    // ゲームの終了を伝える
    public void announceFinish() {
        for (int i = 0; i < MAX_PLAYER; i++) {
            out[i].println("FINISH");
            out[i].flush();
        }
    }

    // ready
    public boolean isReady() {
        return isBattling;
    }

    // 成績を送る
    public void annouceScore(Score score) {
        for (int i = 0; i < MAX_PLAYER; i++) {
            // debug
            System.out.println("in ts: score null?+" + score);

            // プレイヤー数を送る
            out[i].println("RESULT:PLAYERNUM:" + score.playerNum);
            out[i].flush();

            // 塗られたフィールド数を送る
            for (int j = 0; j < score.teamNum; j++)
                out[i].println("RESULT:FIELD:" + j + ":" + score.painted[j]);
            // キル数，デス数を送る
            for (int j = 0; j < score.playerNum; j++) {
                out[i].println("RESULT:KILL:" + j + ":" + score.kill[j]);
                out[i].println("RESULT:DEATH:" + j + ":" + score.death[j]);
            }
            out[i].flush();
        }
    }

    // ゲームの開始を伝える（色の組み合わせも送る）
    public void announceReady(int colorPair) {
        for (int i = 0; i < MAX_PLAYER; i++) {
            out[i].println("READY:COLOR:" + colorPair + ":ID:" + i);
            out[i].flush();
        }
    }


    // ゲームを終了させる
    public void finishGame() {
        isBattling = false;
        Socket socket;
        try {
            socket = new Socket("127.0.0.1", PORT); // 追い返そうと待機しているサーバに接続
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void run() {
        int n = 0;

        try {
            ServerSocket server = new ServerSocket(PORT); // サーバ起動
            System.out.println("The server has launched!");
            // 待ち受け
            while (true) {
                incoming[n] = server.accept(); // 接続要求をを待ち続ける
                System.out.println("Accept client No." + n);

                // 必要な入出力ストリームを作成する
                isr[n] = new InputStreamReader(incoming[n].getInputStream());
                in[n] = new BufferedReader(isr[n]);
                out[n] = new PrintWriter(incoming[n].getOutputStream(), true);

                out[n].println("CONNECTED"); // 接続完了の合図

                // 必要なパラメータを渡しスレッドを作成
                myClientProcThread[n] =
                        new ClientProcThread(this, n, incoming[n], isr[n], in[n], out[n]);
                
                myClientProcThread[n].start(); // スレッドを開始する

                n++;
                member = n; // メンバーの数を更新する

                if (member == MAX_PLAYER) { // 人数が揃ったら開始
                    isBattling = true;
                    break;
                }
            }

            // 戦闘中は接続依頼を追い返す
            while (isBattling) {
                System.out.println("追い返してる");
                Socket s = server.accept();
                (new PrintWriter(s.getOutputStream(), true)).println("BUSY");

                s.close();

                System.out.println("reject a connection"); // TODO debug
            }

            // 戦闘終了処理
            server.close();
            System.out.println("battle end"); // debug

        } catch (Exception e) {
            System.err.println("ソケット作成時にエラーが発生しました: ");
            e.printStackTrace();
        }
    }

    // ClientProcThreadから呼び出す
    public void setHuman(int playerID, Direction dir) {
        // TODO debug
        System.out.println("setHuman:" + playerID + ":" + dir.name());

        direction[playerID] = dir;
    }

    public void setBomb(int playerID) {
        bombs[playerID]++;
    }
}
