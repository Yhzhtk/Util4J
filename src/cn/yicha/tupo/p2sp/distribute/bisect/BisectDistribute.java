package cn.yicha.tupo.p2sp.distribute.bisect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.locks.ReentrantLock;

import cn.yicha.tupo.p2sp.distribute.Distribute;
import cn.yicha.tupo.p2sp.entity.RangeComparator;
import cn.yicha.tupo.p2sp.entity.RangeFactory;
import cn.yicha.tupo.p2sp.entity.RangeInfo;
import cn.yicha.tupo.p2sp.entity.StartComparator;
import cn.yicha.tupo.p2sp.entity.UriInfo;

/**
 * 剩余最大块取二分法操作
 * 
 * @author yicha
 * 
 */
public class BisectDistribute implements Distribute {

	private int baseSize; // 下载判断块大小
	private int fileSize;
	private List<UriInfo> uris;

	private int rangeIndex = 0;
	private volatile SortedSet<RangeInfo> fillRanges;
	private volatile List<RangeInfo> emptyRanges;
	
	private int minRangeSize = 10240;
	private Comparator<RangeInfo> rangeComparator;
	
	private ReentrantLock lock = new ReentrantLock();

	public BisectDistribute(int fileSize) {
		this(fileSize, new ArrayList<UriInfo>());
	}

	public BisectDistribute(int fileSize, List<UriInfo> uris) {
		fillRanges = new TreeSet<RangeInfo>(new StartComparator());
		emptyRanges = new ArrayList<RangeInfo>();
		
		rangeComparator = new RangeComparator();

		this.baseSize = 1024;
		this.fileSize = fileSize;
		this.uris = uris;

		// 初始时按线程平分
		if (uris.size() == 0) {
			RangeInfo r = RangeFactory.getRangeInstance(rangeIndex++);
			r.setStart(0);
			r.setEnd(fileSize);
			addEmpty(r);
		} else {
			int len = uris.size();
			int esize = fileSize / len;
			for (int i = 0; i < len; i++) {
				int s = i * esize;
				int e = s + esize;
				if (i == len - 1) {
					e = fileSize;
				}
				RangeInfo r = RangeFactory.getRangeInstance(rangeIndex++);
				r.setStart(s);
				r.setEnd(e);
				addEmpty(r);
			}
		}
	}

	@Override
	public synchronized void addUri(UriInfo uri) {
		this.uris.add(uri);
	}

	@Override
	public synchronized void deleteUri(UriInfo uri) {
		this.uris.remove(uri);
		if(uri != null && uri.getNowRange() != null){
			// 当前正在下载的设为未使用
			RangeInfo r = uri.getNowRange();
			r.setUsed(false);
		}
	}

	@Override
	public synchronized RangeInfo getNextRangeInfo() {
		lock.lock();
		RangeInfo r = getMaxEmptyRange();
		if (r == null) {
			lock.unlock();
			return null;
		}
		// 未使用直接返回
		if(!r.isUsed()){
			r.setUsed(true);
			lock.unlock();
			return r;
		}
		
		// 小于最小允许的大小则不需要再分了
		if(r.getEnd() - r.getStart() < minRangeSize){
			lock.unlock();
			return null;
		}
		
		int s = r.getStart();
		int e = r.getEnd();
		int m = (s + e) / 2;
		r.setEnd(m);

		RangeInfo n = RangeFactory.getRangeInstance(rangeIndex++);
		n.setStart(m);
		n.setEnd(e);
		addEmpty(n);

		n.setUsed(true);
		lock.unlock();
		return n;
	}

	@Override
	public synchronized void collectRangeInfo(RangeInfo range) {
		lock.lock();
		range.setUsed(false);
		if(range.getStart() >= range.getEnd()){
			removeEmpty(range);
		}
		lock.unlock();
	}

	@Override
	public synchronized boolean isCompleted() {
		return emptyRanges.size() == 0;
	}

	@Override
	public List<UriInfo> getAllUriInfos() {
		return uris;
	}

	public int getBaseSize() {
		return baseSize;
	}

	public long getFileSize() {
		return fileSize;
	}

	public synchronized void setBytesOk(final RangeInfo range, int blen) {
		lock.lock();
		int loc = range.getStart();
		// 设置填充信息
		setFillInfo(loc, blen);
		// 设置未下载信息
		range.setStart(loc + blen);
		lock.unlock();
	}

	public synchronized boolean hasBytesDown(int loc) {
		lock.lock();
		boolean res = false;
		Iterator<RangeInfo> iter = fillRanges.iterator();
		while (iter.hasNext()) {
			RangeInfo r = iter.next();
			if(r.getStart() >= r.getEnd()){
				continue;
			}
			// 不能等于end
			if (loc >= r.getStart() && loc < r.getEnd()) {
				res = true;
				break;
			}
		}
		lock.unlock();
		return res;
	}

	private void setFillInfo(int loc, int blen) {
		Iterator<RangeInfo> iter = fillRanges.iterator();
		RangeInfo r = null;
		while (iter.hasNext()) {
			r = iter.next();
			if (loc >= r.getStart() && loc <= r.getEnd()) {
				break;
			}
			r = null;
		}
		if (r == null) {
			r = RangeFactory.getRangeInstance(rangeIndex++);
			r.setStart(loc);
			fillRanges.add(r);
		}
		// 设置结尾处
		r.setEnd(Math.max(loc + blen, r.getEnd()));
	}

	private void addEmpty(RangeInfo r) {
		emptyRanges.add(r);
	}

	public void removeEmpty(RangeInfo r) {
		emptyRanges.remove(r);
		RangeFactory.releaseRangeInfo(r);
	}

	private RangeInfo getMaxEmptyRange() {
		if (emptyRanges.size() > 0) {
			Collections.sort(emptyRanges, rangeComparator);
			return emptyRanges.get(0);
		}
		return null;
	}
}
