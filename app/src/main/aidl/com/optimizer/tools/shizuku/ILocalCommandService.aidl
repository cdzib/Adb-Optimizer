package com.optimizer.tools.shizuku;

import android.os.Bundle;

interface ILocalCommandService {
    void destroy() = 16777114;
    Bundle execute(String command, long timeoutMs) = 1;
}
