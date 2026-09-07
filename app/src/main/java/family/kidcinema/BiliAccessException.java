package family.kidcinema;

import java.io.IOException;

/** Server refusal, kept separate from an empty catalogue or a transport timeout. */
final class BiliAccessException extends IOException {
    final int code;
    BiliAccessException(int code){super("B 站暂时限制访问（"+code+"），已保留缓存。");this.code=code;}
    static boolean riskCode(int code){return code==403||code==412||code==429||code==-352||code==-401||code==-403||code==-412||code==-509;}
}
