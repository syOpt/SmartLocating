function sigPro (x, nWin)
    nTotal = length(x);
    n = 0:nWin-1;
    fs = 44100;
    %f = (0:nWin-1)*fs/nWin;
    nBuff = fix(nTotal/nWin);
    fIndex15000 = 15000*nWin/fs+1;
    mag15000 = 1:nBuff;
    %mag15000 = 1:nTotal-nWin; 
    pha15000 = 1:nBuff;
    %pha15000 = 1:nTotal-nWin; 
    for i = 0:nBuff-1
    %for i = 0:nTotal-nWin-1
        xWin = x(1+i*nWin:nWin+i*nWin); 
        %xWin = x(i+1:i+nWin);
        yWin = fft(xWin, nWin);
        yWinMag = abs(yWin)*2/nWin;
        yWinPha = phase(yWin);
        mag15000(i+1) = yWinMag(fIndex15000);
        pha15000(i+1) = yWinPha(fIndex15000);
    end
    t = (1:nBuff)*nWin/fs; 
    %t = (1:nTotal-nWin)/fs;
    plot(t, mag15000);
    pause();
    plot(t, pha15000);
    
end