# load in the files variable
source ~/.dot_files/files.sh

mkdir ~/.old_dot_files

for i in ${files[@]}; do
  echo "Moving: " $i
  mv ~/$i ~/.old_dot_files
  echo "Copying: " $i
  cp  ~/.dot_files/$i ~
done

source ~/.bashrc
