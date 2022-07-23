import React from 'react';


function Avatar(props: {
  color: string,
  initials: string,
  label: string,
  subLabel?: string
}) {
  return (
    <div style={{padding: 5, height: 50, display: 'flex'}}>
      <div style={{
        backgroundColor: props.color, 
        width: 40, 
        height: 40,
        borderRadius: 10,
        marginRight: 7,
        textAlign: 'center',
        fontSize: 20,
        fontWeight: 'bold',
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        overflow: 'hidden',
      }}>
        {props.initials}
      </div>
      <div style={{
        height: 40,
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'center',
        alignItems: 'left',
      }}>
        <div style={{fontWeight: 'bold'}}>
          {props.label}
        </div>
        <div style={{fontSize: 12}}>
          { props.subLabel && <div>
            {props.subLabel.substring(0, 15)}
            {(props.subLabel.length > 15) && "..."}
          </div>}
        </div>
      </div>
    </div>
  );
}

function getInitials (name: string, splitter: string) {
  if (name) {
    return name.split(splitter).slice(0, 2).map((i) => (i.substring(0, 1).toUpperCase())).join("");
  } else {
    return "";
  }
};

export {
  Avatar,
  getInitials,
};
