package com.thm.videosample.adapter;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import com.quickblox.users.model.QBUser;
import com.thm.videosample.R;
import com.thm.videosample.utils.SharedPrefsHelper;

import java.util.ArrayList;

public class OpponentAdapter extends RecyclerView.Adapter<OpponentAdapter.ViewHolder> {
    private ArrayList<QBUser> mOpponentsList;
    private ArrayList<QBUser> mSelectedList;
    private Context mContext;

    public OpponentAdapter(Context context, ArrayList<QBUser> opponentsList) {
        mContext = context;
        mOpponentsList = opponentsList;
        mSelectedList = new ArrayList<>();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_opponent, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        QBUser currentUser = SharedPrefsHelper.getInstance(mContext).getQbUser();
        QBUser user = mOpponentsList.get(position);
        holder.mTextOpponent.setText(user.getLogin());
        if (currentUser.getLogin().equals(user.getLogin())) {
            holder.mCheckBoxOpponent.setVisibility(View.GONE);
        } else {
            holder.mCheckBoxOpponent.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    checkItem(holder.getAdapterPosition());
                }
            });
        }
    }

    private void checkItem(int position) {
        QBUser selectedUser = mOpponentsList.get(position);
        if (mSelectedList.contains(selectedUser)) {
            mSelectedList.remove(selectedUser);
        } else {
            mSelectedList.add(selectedUser);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return mOpponentsList != null ? mOpponentsList.size() : 0;
    }

    public ArrayList<Integer> getSelectedOpponentsList() {
        ArrayList<Integer> result = new ArrayList<>();
        for (QBUser user : mSelectedList) {
            result.add(user.getId());
        }
        return result;
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private CheckBox mCheckBoxOpponent;
        private TextView mTextOpponent;

        public ViewHolder(View itemView) {
            super(itemView);
            mCheckBoxOpponent = itemView.findViewById(R.id.cb_opponent);
            mTextOpponent = itemView.findViewById(R.id.tv_opponent_name);
        }
    }
}
